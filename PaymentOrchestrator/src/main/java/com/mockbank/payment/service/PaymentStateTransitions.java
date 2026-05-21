package com.mockbank.payment.service;

import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Chuyển trạng thái payment theo saga bill-pay — không hạ state terminal / đang capture.
 */
public final class PaymentStateTransitions {

  private static final Set<PaymentState> PROTECTED_FROM_DOWNGRADE = EnumSet.of(
      PaymentState.POSTED,
      PaymentState.FAILED,
      PaymentState.RECONCILIATION_REQUIRED,
      PaymentState.CAPTURING);

  /** Đã placeHold, chờ settlement — có thể nhận POSTED và capture. */
  public static final Set<PaymentState> AWAITING_SETTLEMENT = EnumSet.of(
      PaymentState.FUNDS_HELD,
      PaymentState.BATCHED,
      PaymentState.SUBMITTED);

  private PaymentStateTransitions() {
  }

  public static boolean canBeginCapture(PaymentState state) {
    return state != null && AWAITING_SETTLEMENT.contains(state);
  }

  public static boolean isTerminal(PaymentState state) {
    return state == PaymentState.POSTED || state == PaymentState.FAILED;
  }

  public static boolean canAdvanceTo(PaymentState current, PaymentState target) {
    if (current == null || target == null) {
      return false;
    }
    if (PROTECTED_FROM_DOWNGRADE.contains(current)) {
      return false;
    }
    return switch (target) {
      case BATCHED -> current == PaymentState.FUNDS_HELD;
      case SUBMITTED -> current == PaymentState.BATCHED;
      default -> false;
    };
  }

  /**
   * @return true nếu đã chuyển sang BATCHED; false nếu bỏ qua (vd. đã POSTED)
   */
  public static boolean applyBatched(Payment payment, UUID batchId) {
    if (!canAdvanceTo(payment.getState(), PaymentState.BATCHED)) {
      if (payment.getBatchId() == null && batchId != null) {
        payment.setBatchId(batchId);
      }
      return false;
    }
    payment.setState(PaymentState.BATCHED);
    payment.setBatchId(batchId);
    return true;
  }

  /**
   * @return true nếu đã chuyển sang SUBMITTED
   */
  public static boolean applySubmitted(Payment payment) {
    if (!canAdvanceTo(payment.getState(), PaymentState.SUBMITTED)) {
      return false;
    }
    payment.setState(PaymentState.SUBMITTED);
    return true;
  }
}
