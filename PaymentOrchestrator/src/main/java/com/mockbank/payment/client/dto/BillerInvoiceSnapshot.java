package com.mockbank.payment.client.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BillerInvoiceSnapshot(
    UUID id,
    String customerId,
    String billerReferenceNumber,
    BigDecimal amount,
    String currency,
    String status) {
}
