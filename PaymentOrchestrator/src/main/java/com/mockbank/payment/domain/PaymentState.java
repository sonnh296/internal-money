package com.mockbank.payment.domain;

public enum PaymentState {
  FUNDS_HELD,
  /** Đang capture hold — chặn xử lý song song trước khi gọi AccountService */
  CAPTURING,
  BATCHED,
  SUBMITTED,
  POSTED,
  FAILED
}
