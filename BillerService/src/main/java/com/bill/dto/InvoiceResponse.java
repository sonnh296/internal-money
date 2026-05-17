package com.bill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceResponse {
    private UUID id;
    private UUID subscriptionId;
    private String customerId;
    private UUID packageId;
    private String packageName;
    private String billerReferenceNumber;
    private BigDecimal amount;
    private String currency;
    private LocalDate dueDate;
    private String status;
    private OffsetDateTime createdAt;
}
