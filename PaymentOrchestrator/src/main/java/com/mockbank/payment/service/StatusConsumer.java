package com.mockbank.payment.service;

import com.mockbank.commons.dto.account.PostingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockbank.payment.client.AccountM2MClient;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.domain.ProcessedEvent;
import com.mockbank.commons.dto.events.billpay.BillpayStatusEvent;
import com.mockbank.payment.repo.PaymentRepo;
import com.mockbank.payment.repo.ProcessedEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StatusConsumer {
  private final PaymentRepo paymentRepo;
  private final ProcessedEventRepo processed;
  private final ObjectMapper om;
  private final AccountM2MClient accountM2MClient;
  private final TransactionTemplate paymentTransactionTemplate;
  private final BillPayCompensationService compensation;
  private final PaymentCompletionService completion;

  /**
   * Saga POSTED: (1) TX → CAPTURING (2) HTTP capture + mark invoice (3) TX → POSTED.
   * Không debit trước khi ghi nhận CAPTURING — tránh tiền đã trừ mà payment vẫn FUNDS_HELD.
   */
  @RetryableTopic(
      attempts = "4",
      backoff = @Backoff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR)
  @KafkaListener(topics="${payments.topics.billpay-status:billpay.status}", groupId="payment-api")
  public void onMessage(String message) throws Exception {
    var evt = om.readValue(message, BillpayStatusEvent.class);
    if (processed.existsByHandlerAndEventId("status", evt.eventId().toString())) {
      return;
    }

    Payment p = paymentRepo.findById(evt.paymentId()).orElse(null);
    if (p == null) {
      return;
    }

    if ("POSTED".equalsIgnoreCase(evt.status())) {
      handlePosted(evt, p);
    } else {
      handleFailed(evt, p);
    }
  }

  private void handlePosted(BillpayStatusEvent evt, Payment p) throws Exception {
    if (p.getState() == PaymentState.POSTED) {
      markProcessed(evt);
      return;
    }

    if (p.getState() == PaymentState.FAILED) {
      log.warn("Bỏ qua POSTED — payment đã FAILED paymentId={}", p.getPaymentId());
      markProcessed(evt);
      return;
    }

    if (p.getState() == PaymentState.RECONCILIATION_REQUIRED) {
      if (completion.tryCompleteInvoiceAndPost(p, evt.reason(), evt.eventId())) {
        return;
      }
      throw new IllegalStateException(
          "Payment still awaiting invoice reconciliation paymentId=" + p.getPaymentId());
    }

    if (!ensureCapturingState(p, evt)) {
      return;
    }

    p = paymentRepo.findById(evt.paymentId()).orElseThrow();
    BigDecimal debitAmount = p.getAmountValue();
    String captureIdempotencyKey = p.getPaymentId() + ":DEBIT";
    PostingRequest posting = new PostingRequest(debitAmount, p.getReason());

    try {
      captureHoldWithCb(p.getDebtorAccountId(), p.getPaymentId(), captureIdempotencyKey, posting);
    } catch (feign.FeignException ex) {
      if (ex.status() >= 400 && ex.status() < 500 && ex.status() != 408 && ex.status() != 429) {
        compensateFailedCapture(p, evt, ex);
      }
      throw ex;
    } catch (Exception ex) {
      throw ex;
    }

    try {
      completion.markInvoicePaid(p);
    } catch (Exception ex) {
      completion.markReconciliationRequired(p.getPaymentId(), ex.getMessage());
      log.warn("Debit OK nhưng mark invoice thất bại — chuyển RECONCILIATION_REQUIRED paymentId={}",
          p.getPaymentId());
      return;
    }

    completion.finalizeToPosted(p.getPaymentId(), evt.reason(), evt.eventId());
  }

  /**
   * Chuyển trạng thái chờ settlement → CAPTURING trong TX ngắn trước mọi HTTP call tới Account/Biller.
   */
  private boolean ensureCapturingState(Payment p, BillpayStatusEvent evt) {
    if (p.getState() == PaymentState.CAPTURING
        || p.getState() == PaymentState.RECONCILIATION_REQUIRED) {
      return true;
    }
    if (!PaymentStateTransitions.canBeginCapture(p.getState())) {
      log.warn("Bỏ qua POSTED: trạng thái payment không hợp lệ state={} paymentId={}",
          p.getState(), p.getPaymentId());
      markProcessed(evt);
      return false;
    }

    Boolean proceed = paymentTransactionTemplate.execute(status -> {
      Payment fresh = paymentRepo.findById(p.getPaymentId()).orElseThrow();
      if (PaymentStateTransitions.isTerminal(fresh.getState())) {
        return Boolean.FALSE;
      }
      if (fresh.getState() == PaymentState.CAPTURING
          || fresh.getState() == PaymentState.RECONCILIATION_REQUIRED) {
        return Boolean.TRUE;
      }
      if (!PaymentStateTransitions.canBeginCapture(fresh.getState())) {
        return Boolean.FALSE;
      }
      fresh.setState(PaymentState.CAPTURING);
      fresh.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(fresh);
      return Boolean.TRUE;
    });

    if (Boolean.FALSE.equals(proceed)) {
      paymentRepo.findById(p.getPaymentId()).ifPresent(latest -> {
        if (PaymentStateTransitions.isTerminal(latest.getState())) {
          markProcessed(evt);
        }
      });
      return false;
    }
    return true;
  }

  /**
   * Capture thất bại: đánh dấu FAILED trong TX rồi giải phóng hold (hold vẫn ACTIVE nếu chưa capture).
   */
  private void compensateFailedCapture(Payment p, BillpayStatusEvent evt, Exception cause) {
    log.warn("Capture thất bại, bù trừ: paymentId={} reason={}", p.getPaymentId(), cause.getMessage());
    paymentTransactionTemplate.execute(status -> {
      Payment fresh = paymentRepo.findById(p.getPaymentId()).orElseThrow();
      if (fresh.getState() == PaymentState.POSTED) {
        return null;
      }
      if (fresh.getState() != PaymentState.FAILED) {
        fresh.setState(PaymentState.FAILED);
        fresh.setReason("capture_failed: " + evt.reason());
        fresh.setUpdatedAt(OffsetDateTime.now());
        paymentRepo.save(fresh);
      }
      return null;
    });
    compensation.releaseHoldAfterFailure(p.getDebtorAccountId(), p.getPaymentId());
  }

  private void handleFailed(BillpayStatusEvent evt, Payment p) {
    if (p.getState() == PaymentState.FAILED || p.getState() == PaymentState.POSTED) {
      markProcessed(evt);
      return;
    }
    if (p.getState() == PaymentState.RECONCILIATION_REQUIRED) {
      log.warn("Bỏ qua FAILED status — đã debit, đang reconciliation paymentId={}", p.getPaymentId());
      markProcessed(evt);
      return;
    }
    try {
      releaseHoldWithCb(p.getDebtorAccountId(), p.getPaymentId());
    } catch (Exception ex) {
      log.error("Release hold thất bại, sẽ retry Kafka: paymentId={}", p.getPaymentId(), ex);
      throw ex;
    }
    paymentTransactionTemplate.execute(status -> {
      Payment fresh = paymentRepo.findById(p.getPaymentId()).orElseThrow();
      if (fresh.getState() == PaymentState.FAILED) {
        if (!processed.existsByHandlerAndEventId("status", evt.eventId().toString())) {
          processed.save(ProcessedEvent.builder()
              .handler("status").eventId(evt.eventId().toString())
              .processedAt(OffsetDateTime.now()).build());
        }
        return null;
      }
      fresh.setState(PaymentState.FAILED);
      fresh.setReason(evt.reason());
      fresh.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(fresh);
      processed.save(ProcessedEvent.builder()
          .handler("status").eventId(evt.eventId().toString())
          .processedAt(OffsetDateTime.now()).build());
      return null;
    });
  }

  private void markProcessed(BillpayStatusEvent evt) {
    paymentTransactionTemplate.execute(status -> {
      if (!processed.existsByHandlerAndEventId("status", evt.eventId().toString())) {
        processed.save(ProcessedEvent.builder()
            .handler("status").eventId(evt.eventId().toString())
            .processedAt(OffsetDateTime.now()).build());
      }
      return null;
    });
  }

  @CircuitBreaker(name = "accountClient")
  public void releaseHoldWithCb(UUID accountId, UUID holdId) {
    accountM2MClient.releaseHold(accountId, holdId);
  }

  @CircuitBreaker(name = "accountClient")
  public void captureHoldWithCb(UUID accountId, UUID holdId, String idempotencyKey, PostingRequest r) {
    accountM2MClient.captureHoldAndDebit(accountId, holdId, idempotencyKey, r);
  }

  @org.springframework.kafka.annotation.DltHandler
  public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    log.error("DLT [StatusConsumer]: message không xử lý được sau tất cả các lần retry. topic={} payload={}",
        topic, message);
  }

}
