package com.mockbank.commons.dto.account;

import jakarta.validation.constraints.NotBlank;

public record ProvisionAccountRequest(
        @NotBlank String customerId,
        String displayName
) {}
