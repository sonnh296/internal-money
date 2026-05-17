package com.account.dto;


import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String type,
        String status,
        BigDecimal amount,
        String reason,
        BigDecimal balanceAfter,
        /** IN = tiền vào, OUT = tiền ra */
        String flowDirection,
        String counterpartyName,
        String counterpartyAccountNumber
) {}
