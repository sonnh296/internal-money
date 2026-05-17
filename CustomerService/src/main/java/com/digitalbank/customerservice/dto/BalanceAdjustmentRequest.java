package com.digitalbank.customerservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BalanceAdjustmentRequest(
        @NotBlank String customerId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String type,
        @NotBlank String reason,
        @NotNull BigDecimal balanceAfter
) {}
