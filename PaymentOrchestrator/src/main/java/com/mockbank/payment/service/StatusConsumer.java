package com.mockbank.payment.service;

import com.mockbank.commons.dto.account.PostingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockbank.payment.client.AccountM2MClient;
import com.mockbank.payment.client.BillerInvoiceM2MClient;
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
import org.springframework.kafka.support.KafkaHeaders;
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
  private final BillerInvoiceM2MClient billerInvoiceClient;
  private final TransactionTemplate paymentTransactionTemplate;
  private final BillPayCompensationService compensation;

  /**
   * Saga POSTED: (1) TX → CAPTURING (2) HTTP capture + mark invoice (3) TX → POSTED + outbox.
   * Không debit trước khi ghi nhận CAPTURING — tránh tiền đã trừ mà payment vẫn FUNDS_HELD.
   */
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

    if (!ensureCapturingState(p, evt)) {
      return;
    }

    p = paymentRepo.findById(evt.paymentId()).orElseThrow();
    BigDecimal debitAmount = p.getAmountValue();
    String captureIdempotencyKey = p.getPaymentId() + ":DEBIT";
    PostingRequest posting = new PostingRequest(debitAmount, p.getReason());

    try {
      captureHoldWithCb(p.getDebtorAccountId(), p.getPaymentId(), captureIdempotencyKey, posting);
      markInvoicePaid(p);
    } catch (Exception ex) {
      compensateFailedCapture(p, evt, ex);
      throw ex;
    }

    finalizePosted(evt, p);
  }

  /**
   * Chuyển FUNDS_HELD → CAPTURING trong TX ngắn trước mọi HTTP call tới Account/Biller.
   */
  private boolean ensureCapturingState(Payment p, BillpayStatusEvent evt) {
    if (p.getState() == PaymentState.CAPTURING) {
      return true;
    }
    if (p.getState() != PaymentState.FUNDS_HELD) {
      log.warn("Bỏ qua POSTED: trạng thái payment không hợp lệ state={} paymentId={}",
          p.getState(), p.getPaymentId());
      return false;
    }

    Boolean proceed = paymentTransactionTemplate.execute(status -> {
      Payment fresh = paymentRepo.findById(p.getPaymentId()).orElseThrow();
      if (fresh.getState() == PaymentState.POSTED) {
        return Boolean.FALSE;
      }
      if (fresh.getState() == PaymentState.CAPTURING) {
        return Boolean.TRUE;
      }
      if (fresh.getState() != PaymentState.FUNDS_HELD) {
        return Boolean.FALSE;
      }
      fresh.setState(PaymentState.CAPTURING);
      fresh.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(fresh);
      return Boolean.TRUE;
    });

    if (Boolean.FALSE.equals(proceed)) {
      paymentRepo.findById(p.getPaymentId()).ifPresent(latest -> {
        if (latest.getState() == PaymentState.POSTED) {
          markProcessed(evt);
        }
      });
      return false;
    }
    return true;
  }

  private void finalizePosted(BillpayStatusEvent evt, Payment p) {
    paymentTransactionTemplate.execute(status -> {
      Payment fresh = paymentRepo.findById(p.getPaymentId()).orElseThrow();
      if (fresh.getState() == PaymentState.POSTED) {
        if (!processed.existsByHandlerAndEventId("status", evt.eventId().toString())) {
          processed.save(ProcessedEvent.builder()
              .handler("status").eventId(evt.eventId().toString())
              .processedAt(OffsetDateTime.now()).build());
        }
        return null;
      }
      if (fresh.getState() != PaymentState.CAPTURING) {
        throw new IllegalStateException(
            "Cannot finalize POSTED from state " + fresh.getState() + " paymentId=" + fresh.getPaymentId());
      }
      fresh.setState(PaymentState.POSTED);
      fresh.setReason(evt.reason());
      fresh.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(fresh);
      processed.save(ProcessedEvent.builder()
          .handler("status").eventId(evt.eventId().toString())
          .processedAt(OffsetDateTime.now()).build());
      return null;
    });
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
      if (!processed.existsByHandlerAndEventId("status", evt.eventId().toString())) {
        processed.save(ProcessedEvent.builder()
            .handler("status").eventId(evt.eventId().toString())
            .processedAt(OffsetDateTime.now()).build());
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

  private void markInvoicePaid(Payment p) {
    try {
      UUID invoiceId = UUID.fromString(p.getInvoiceReference());
      billerInvoiceClient.markPaid(invoiceId);
      log.info("Marked invoice {} as PAID for paymentId={}", invoiceId, p.getPaymentId());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid invoice reference on payment {}: {}", p.getPaymentId(), p.getInvoiceReference());
    } catch (Exception e) {
      log.error("Failed to mark invoice {} as PAID for payment {}: {}",
          p.getInvoiceReference(), p.getPaymentId(), e.getMessage());
      throw e;
    }
  }

  @CircuitBreaker(name = "accountClient")
  private void releaseHoldWithCb(UUID accountId, UUID holdId) {
    accountM2MClient.releaseHold(accountId, holdId);
  }

  @CircuitBreaker(name = "accountClient")
  private void captureHoldWithCb(UUID accountId, UUID holdId, String idempotencyKey, PostingRequest r) {
    accountM2MClient.captureHoldAndDebit(accountId, holdId, idempotencyKey, r);
  }

  @org.springframework.kafka.annotation.DltHandler
  public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    log.error("DLT [StatusConsumer]: message không xử lý được sau tất cả các lần retry. topic={} payload={}",
        topic, message);
  }

}
