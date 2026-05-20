package com.mockbank.commons.dto.account;

public record AccountLookupResponse(
        String accountNumber,
        String displayName,
        String currency,
        AccountStatus status
) {}
