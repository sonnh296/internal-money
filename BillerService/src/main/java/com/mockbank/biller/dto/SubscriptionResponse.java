package com.mockbank.biller.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {
    private UUID id;
    private String customerId;
    private UUID packageId;
    private String packageName;
    private String packageCategory;
    private String packageReferenceNumber;
    private BigDecimal packageMonthlyAmount;
    private String packageCurrency;
    private String status;
    private OffsetDateTime createdAt;
}
