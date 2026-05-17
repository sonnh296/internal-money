package com.payments.orch.dto;

import java.math.BigDecimal;

public record RewardRedeemResponse(
        String customerId,
        long redeemedPoints,
        BigDecimal redeemedAmount,
        long remainingPoints,
        String status
) {}
