package com.account.dto;

import jakarta.validation.constraints.NotBlank;

public record ProvisionAccountRequest(
        @NotBlank String customerId,
        String displayName
) {}
