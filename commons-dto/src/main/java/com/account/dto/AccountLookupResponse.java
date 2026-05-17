package com.account.dto;

public record AccountLookupResponse(
        String accountNumber,
        String displayName,
        String currency,
        AccountStatus status
) {}
