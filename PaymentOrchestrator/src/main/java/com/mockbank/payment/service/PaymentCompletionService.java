package com.mockbank.payment.service;

import com.mockbank.payment.client.BillerInvoiceM2MClient;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.domain.ProcessedEvent;
import com.mockbank.payment.repo.PaymentRepo;
import com.mockbank.payment.repo.ProcessedEventRepo;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Hoàn tất saga sau capture: mark invoice PAID và chuyển payment → POSTED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletionService {

  private final PaymentRepo paymentRepo;
  private final ProcessedEventRepo processed;
  private final BillerInvoiceM2MClient billerInvoiceClient;
  private final TransactionTemplate paymentTransactionTemplate;

  public void markInvoicePaid(Payment p) {
    try {
      UUID invoiceId = UUID.fromString(p.getInvoiceReference());
      billerInvoiceClient.markPaid(invoiceId);
      log.info("Marked invoice {} as PAID for paymentId={}", invoiceId, p.getPaymentId());
    } catch (IllegalArgumentException e) {
      log.warn("Invalid invoice reference on payment {}: {}", p.getPaymentId(), p.getInvoiceReference());
    } catch (FeignException e) {
      if (e.status() == 409) {
        log.info("Invoice {} already PAID for paymentId={}", p.getInvoiceReference(), p.getPaymentId());
        return;
      }
      log.error("Failed to mark invoice {} as PAID for payment {}: {}",
          p.getInvoiceReference(), p.getPaymentId(), e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Failed to mark invoice {} as PAID for payment {}: {}",
          p.getInvoiceReference(), p.getPaymentId(), e.getMessage());
      throw e;
    }
  }

  public void markReconciliationRequired(UUID paymentId, String detail) {
    paymentTransactionTemplate.execute(status -> {
      Payment fresh = paymentRepo.findById(paymentId).orElseThrow();
      if (fresh.getState() == PaymentState.POSTED || fresh.getState() == PaymentState.RECONCILIATION_REQUIRED) {
        return null;
      }
      fresh.setState(PaymentState.RECONCILIATION_REQUIRED);
      fresh.setReason("invoice_mark_failed_after_capture: " + detail);
      fresh.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(fresh);
      return null;
    });
  }

  /**
   * @return true nếu đã POSTED
   */
  public boolean tryCompleteInvoiceAndPost(Payment p, String postedReason, UUID statusEventId) {
    Payment latest = paymentRepo.findById(p.getPaymentId()).orElseThrow();
    if (latest.getState() == PaymentState.POSTED) {
      return true;
    }
    if (latest.getState() != PaymentState.RECONCILIATION_REQUIRED
        && latest.getState() != PaymentState.CAPTURING) {
      return false;
    }
    try {
      markInvoicePaid(latest);
    } catch (Exception ex) {
      markReconciliationRequired(p.getPaymentId(), ex.getMessage());
      return false;
    }
    finalizeToPosted(latest.getPaymentId(), postedReason, statusEventId);
    return paymentRepo.findById(p.getPaymentId())
        .map(x -> x.getState() == PaymentState.POSTED)
        .orElse(false);
  }

  public void finalizeToPosted(UUID paymentId, String reason, UUID statusEventId) {
    paymentTransactionTemplate.execute(status -> {
      Payment fresh = paymentRepo.findById(paymentId).orElseThrow();
      if (fresh.getState() == PaymentState.POSTED) {
        if (statusEventId != null) {
          saveProcessedIfAbsent(statusEventId);
        }
        return null;
      }
      if (fresh.getState() != PaymentState.CAPTURING
          && fresh.getState() != PaymentState.RECONCILIATION_REQUIRED) {
        throw new IllegalStateException(
            "Cannot finalize POSTED from state " + fresh.getState() + " paymentId=" + fresh.getPaymentId());
      }
      fresh.setState(PaymentState.POSTED);
      if (reason != null && !reason.isBlank()) {
        fresh.setReason(reason);
      }
      fresh.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(fresh);
      if (statusEventId != null) {
        saveProcessedIfAbsent(statusEventId);
      }
      return null;
    });
  }

  private void saveProcessedIfAbsent(UUID statusEventId) {
    String eventId = statusEventId.toString();
    if (!processed.existsByHandlerAndEventId("status", eventId)) {
      processed.save(ProcessedEvent.builder()
          .handler("status")
          .eventId(eventId)
          .processedAt(OffsetDateTime.now())
          .build());
    }
  }
}
