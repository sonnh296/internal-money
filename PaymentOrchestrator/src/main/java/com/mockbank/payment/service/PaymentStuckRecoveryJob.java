package com.mockbank.payment.service;

import com.mockbank.commons.dto.account.HoldStatus;
import com.mockbank.payment.client.AccountM2MClient;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.repo.PaymentRepo;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStuckRecoveryJob {

  private final PaymentRepo paymentRepo;
  private final BillPayCompensationService compensation;
  private final AccountM2MClient accountM2MClient;
  private final PaymentCompletionService completion;

  @Value("${payments.recovery.capturing-timeout-minutes:30}")
  private long capturingTimeoutMinutes;

  @Scheduled(fixedDelayString = "${payments.recovery.fixed-delay-ms:300000}")
  public void recoverStuckCapturing() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(capturingTimeoutMinutes);
    List<Payment> stuck = paymentRepo.findByStateAndUpdatedAtBefore(PaymentState.CAPTURING, cutoff);
    for (Payment p : stuck) {
      recoverOneCapturing(p);
    }
  }

  private void recoverOneCapturing(Payment p) {
    HoldStatus holdStatus = resolveHoldStatus(p);
    if (holdStatus == HoldStatus.CAPTURED) {
      log.warn("CAPTURING timeout nhưng hold đã capture — reconciliation paymentId={}", p.getPaymentId());
      completion.markReconciliationRequired(p.getPaymentId(), "capturing_timeout_after_debit");
      completion.tryCompleteInvoiceAndPost(p, "capturing_timeout_recovered", null);
      return;
    }
    if (holdStatus == HoldStatus.ACTIVE) {
      log.warn("Recovering stuck CAPTURING (hold ACTIVE) paymentId={}", p.getPaymentId());
      p.setState(PaymentState.FAILED);
      p.setReason("capturing_timeout");
      p.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(p);
      try {
        compensation.releaseHoldAfterFailure(p.getDebtorAccountId(), p.getPaymentId());
      } catch (Exception ex) {
        log.error("Failed to release hold during CAPTURING recovery paymentId={}", p.getPaymentId(), ex);
      }
      return;
    }
    log.warn("CAPTURING timeout with hold status={} paymentId={} — mark RECONCILIATION_REQUIRED",
        holdStatus, p.getPaymentId());
    completion.markReconciliationRequired(p.getPaymentId(), "capturing_timeout_hold_" + holdStatus);
    completion.tryCompleteInvoiceAndPost(p, "capturing_timeout_recovered", null);
  }

  private HoldStatus resolveHoldStatus(Payment p) {
    try {
      var hold = accountM2MClient.getHold(p.getDebtorAccountId(), p.getPaymentId());
      return hold.status();
    } catch (FeignException.NotFound e) {
      return HoldStatus.CAPTURED;
    } catch (Exception ex) {
      log.warn("Could not resolve hold status paymentId={}: {}", p.getPaymentId(), ex.getMessage());
      return HoldStatus.CAPTURED;
    }
  }
}
