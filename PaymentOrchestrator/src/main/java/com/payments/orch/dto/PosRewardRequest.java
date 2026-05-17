package com.payments.orch.dto;

import java.math.BigDecimal;

public record PosRewardRequest(
        String customerId,
        String transactionId,
        BigDecimal amount
) {}
