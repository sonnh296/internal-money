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

/**
 * Giải phóng hold khi bill-pay saga kẹt ở FUNDS_HELD (sau placeHold, chưa capture).
 * Bổ sung cho {@link PaymentStuckRecoveryJob} (chỉ xử lý CAPTURING).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStaleFundsHeldRecoveryJob {

  private final PaymentRepo paymentRepo;
  private final BillPayCompensationService compensation;
  private final AccountM2MClient accountM2MClient;
  private final PaymentCompletionService completion;

  /** Mặc định 20 phút — sau releaseAt hold BILLPAY (15 phút) + buffer */
  @Value("${payments.recovery.stale-funds-held-timeout-minutes:20}")
  private long staleFundsHeldTimeoutMinutes;

  @Scheduled(fixedDelayString = "${payments.recovery.fixed-delay-ms:300000}")
  public void recoverStaleFundsHeld() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(staleFundsHeldTimeoutMinutes);
    List<Payment> stale = paymentRepo.findByStateAndUpdatedAtBefore(PaymentState.FUNDS_HELD, cutoff);
    for (Payment p : stale) {
      recoverOne(p);
    }
  }

  private void recoverOne(Payment p) {
    HoldStatus holdStatus = resolveHoldStatus(p);
    if (holdStatus == HoldStatus.CAPTURED) {
      log.warn("Stale FUNDS_HELD nhưng hold đã capture — reconciliation paymentId={}", p.getPaymentId());
      completion.markReconciliationRequired(p.getPaymentId(), "stale_funds_held_after_debit");
      completion.tryCompleteInvoiceAndPost(p, "stale_funds_held_recovered", null);
      return;
    }
    log.warn("Recovering stale FUNDS_HELD paymentId={} holdStatus={}", p.getPaymentId(), holdStatus);
    p.setState(PaymentState.FAILED);
    p.setReason("stale_funds_held_timeout");
    p.setUpdatedAt(OffsetDateTime.now());
    paymentRepo.save(p);
    try {
      compensation.releaseHoldAfterFailure(p.getDebtorAccountId(), p.getPaymentId());
    } catch (Exception ex) {
      log.error("Failed to release hold for stale FUNDS_HELD paymentId={}", p.getPaymentId(), ex);
    }
  }

  private HoldStatus resolveHoldStatus(Payment p) {
    try {
      return accountM2MClient.getHold(p.getDebtorAccountId(), p.getPaymentId()).status();
    } catch (FeignException.NotFound e) {
      return HoldStatus.RELEASED;
    } catch (Exception ex) {
      log.warn("Could not resolve hold for stale FUNDS_HELD paymentId={}: {}", p.getPaymentId(), ex.getMessage());
      return HoldStatus.ACTIVE;
    }
  }
}
