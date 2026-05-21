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
import java.util.EnumSet;
import java.util.List;

/**
 * Recovery payment kẹt BATCHED/SUBMITTED (đã qua worker/settlement nhưng chưa capture).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentStaleInFlightRecoveryJob {

  private static final List<PaymentState> IN_FLIGHT_STATES = List.copyOf(
      EnumSet.of(PaymentState.BATCHED, PaymentState.SUBMITTED));

  private final PaymentRepo paymentRepo;
  private final BillPayCompensationService compensation;
  private final AccountM2MClient accountM2MClient;
  private final PaymentCompletionService completion;

  @Value("${payments.recovery.stale-in-flight-timeout-minutes:120}")
  private long staleInFlightTimeoutMinutes;

  @Scheduled(fixedDelayString = "${payments.recovery.fixed-delay-ms:300000}")
  public void recoverStaleInFlight() {
    OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(staleInFlightTimeoutMinutes);
    List<Payment> stale = paymentRepo.findByStateInAndUpdatedAtBefore(IN_FLIGHT_STATES, cutoff);
    for (Payment p : stale) {
      recoverOne(p);
    }
  }

  private void recoverOne(Payment p) {
    HoldStatus holdStatus = resolveHoldStatus(p);
    if (holdStatus == HoldStatus.CAPTURED) {
      log.warn("Stale {} nhưng hold đã capture — reconciliation paymentId={}",
          p.getState(), p.getPaymentId());
      completion.markReconciliationRequired(p.getPaymentId(), "stale_in_flight_after_debit");
      completion.tryCompleteInvoiceAndPost(p, "stale_in_flight_recovered", null);
      return;
    }
    log.warn("Recovering stale {} paymentId={} holdStatus={}", p.getState(), p.getPaymentId(), holdStatus);
    p.setState(PaymentState.FAILED);
    p.setReason("stale_in_flight_timeout");
    p.setUpdatedAt(OffsetDateTime.now());
    paymentRepo.save(p);
    try {
      compensation.releaseHoldAfterFailure(p.getDebtorAccountId(), p.getPaymentId());
    } catch (Exception ex) {
      log.error("Failed to release hold for stale in-flight paymentId={}", p.getPaymentId(), ex);
    }
  }

  private HoldStatus resolveHoldStatus(Payment p) {
    try {
      return accountM2MClient.getHold(p.getDebtorAccountId(), p.getPaymentId()).status();
    } catch (FeignException.NotFound e) {
      return HoldStatus.RELEASED;
    } catch (Exception ex) {
      log.warn("Could not resolve hold for stale in-flight paymentId={}: {}", p.getPaymentId(), ex.getMessage());
      return HoldStatus.ACTIVE;
    }
  }
}
