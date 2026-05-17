package com.account.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InternalTransferResponse(
        String transferId,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        BigDecimal fromBalanceAfter,
        BigDecimal toBalanceAfter,
        OffsetDateTime occurredAt
) {}
