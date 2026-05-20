package com.mockbank.payment.dto;

import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentId,
    PaymentState state,
    UUID debtorAccountId,
    String billerRefNumber,
    String invoiceReference,
    LocalDate executionDate,
    BigDecimal amountValue,
    String amountCcy,
    UUID batchId,
    String externalStatusCode,
    String reason,
    String idempotencyKey,
    Integer version,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static PaymentResponse from(Payment p) {
    return new PaymentResponse(
        p.getPaymentId(),
        p.getState(),
        p.getDebtorAccountId(),
        p.getBillerRefNumber(),
        p.getInvoiceReference(),
        p.getExecutionDate(),
        p.getAmountValue(),
        p.getAmountCcy(),
        p.getBatchId(),
        p.getExternalStatusCode(),
        p.getReason(),
        p.getIdempotencyKey(),
        p.getVersion(),
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
