package com.mockbank.payment.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;

class PaymentStateTransitionsTest {

  @Test
  void shouldNotDowngradeFromPostedToBatched() {
    Payment p = Payment.builder().paymentId(UUID.randomUUID()).state(PaymentState.POSTED).build();
    assertFalse(PaymentStateTransitions.applyBatched(p, UUID.randomUUID()));
    assertTrue(p.getState() == PaymentState.POSTED);
  }

  @Test
  void shouldAdvanceFundsHeldToBatched() {
    Payment p = Payment.builder().paymentId(UUID.randomUUID()).state(PaymentState.FUNDS_HELD).build();
    UUID batchId = UUID.randomUUID();
    assertTrue(PaymentStateTransitions.applyBatched(p, batchId));
    assertTrue(p.getState() == PaymentState.BATCHED);
    assertTrue(batchId.equals(p.getBatchId()));
  }

  @Test
  void shouldNotDowngradeFromPostedToSubmitted() {
    Payment p = Payment.builder().paymentId(UUID.randomUUID()).state(PaymentState.POSTED).build();
    assertFalse(PaymentStateTransitions.applySubmitted(p));
  }

  @Test
  void canBeginCaptureFromSubmitted() {
    assertTrue(PaymentStateTransitions.canBeginCapture(PaymentState.SUBMITTED));
    assertTrue(PaymentStateTransitions.canBeginCapture(PaymentState.BATCHED));
    assertFalse(PaymentStateTransitions.canBeginCapture(PaymentState.POSTED));
  }

  @Test
  void shouldAdvanceBatchedToSubmitted() {
    Payment p = Payment.builder().paymentId(UUID.randomUUID()).state(PaymentState.BATCHED).build();
    assertTrue(PaymentStateTransitions.applySubmitted(p));
    assertTrue(p.getState() == PaymentState.SUBMITTED);
  }
}
