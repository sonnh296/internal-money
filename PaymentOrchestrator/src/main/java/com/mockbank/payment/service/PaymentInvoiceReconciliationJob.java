package com.mockbank.payment.service;

import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.repo.PaymentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Retry mark invoice PAID sau khi debit thành công (RECONCILIATION_REQUIRED).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentInvoiceReconciliationJob {

  private final PaymentRepo paymentRepo;
  private final PaymentCompletionService completion;

  @Scheduled(fixedDelayString = "${payments.recovery.reconciliation-delay-ms:60000}")
  public void reconcileInvoiceUpdates() {
    List<Payment> pending = paymentRepo.findByState(PaymentState.RECONCILIATION_REQUIRED);
    for (Payment p : pending) {
      log.info("Reconciling invoice for paymentId={}", p.getPaymentId());
      boolean done = completion.tryCompleteInvoiceAndPost(p, p.getReason(), null);
      if (done) {
        log.info("Reconciliation completed paymentId={}", p.getPaymentId());
      }
    }
  }
}
