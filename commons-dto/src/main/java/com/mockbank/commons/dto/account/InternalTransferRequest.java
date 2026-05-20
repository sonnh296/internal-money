package com.mockbank.commons.dto.account;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InternalTransferRequest(
        UUID fromAccountId,
        @NotBlank String toAccountNumber,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String reason
) {}
