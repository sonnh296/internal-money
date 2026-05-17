package com.payments.orch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RewardRedeemRequest(
        @NotBlank String customerId,
        @NotBlank String transactionId,
        @NotNull @Positive Long points
) {}
