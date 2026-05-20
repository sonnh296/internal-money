package com.mockbank.payment.service;

import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.repo.PaymentRepo;
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

  @Value("${payments.recovery.capturing-timeout-minutes:30}")
  private long capturingTimeoutMinutes;

  @Scheduled(fixedDelayString = "${payments.recovery.fixed-delay-ms:300000}")
  public void recoverStuckCapturing() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(capturingTimeoutMinutes);
    List<Payment> stuck = paymentRepo.findByStateAndUpdatedAtBefore(PaymentState.CAPTURING, cutoff);
    for (Payment p : stuck) {
      log.warn("Recovering stuck CAPTURING paymentId={}", p.getPaymentId());
      p.setState(PaymentState.FAILED);
      p.setReason("capturing_timeout");
      p.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(p);
      try {
        compensation.releaseHoldAfterFailure(p.getDebtorAccountId(), p.getPaymentId());
      } catch (Exception ex) {
        log.error("Failed to release hold during CAPTURING recovery paymentId={}", p.getPaymentId(), ex);
      }
    }
  }
}
