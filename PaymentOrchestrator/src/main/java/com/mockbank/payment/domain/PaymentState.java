package com.mockbank.payment.domain;

public enum PaymentState {
  FUNDS_HELD,
  /** Đang capture hold — chặn xử lý song song trước khi gọi AccountService */
  CAPTURING,
  BATCHED,
  SUBMITTED,
  POSTED,
  FAILED,
  /**
   * Đã capture/debit nhưng chưa cập nhật invoice — cần reconciliation, không release hold.
   */
  RECONCILIATION_REQUIRED
}
