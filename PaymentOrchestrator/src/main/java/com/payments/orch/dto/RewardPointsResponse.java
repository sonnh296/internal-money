package com.payments.orch.dto;

public record RewardPointsResponse(
        String customerId,
        long points,
        String source,
        boolean inSync
) {}
