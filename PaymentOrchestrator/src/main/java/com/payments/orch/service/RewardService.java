package com.payments.orch.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.payments.orch.client.PosRewardClient;
import com.payments.orch.dto.RewardPointsResponse;
import com.payments.orch.dto.RewardRedeemRequest;
import com.payments.orch.dto.RewardRedeemResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RewardService {

    private final PosRewardClient posRewardClient;

    public RewardPointsResponse getPoints(String customerId) {
        Map<String, Object> raw = posRewardClient.getPoints(customerId);
        long primaryPoints = asLong(raw.get("primaryPoints"));
        String source = String.valueOf(raw.getOrDefault("source", "unknown"));
        boolean inSync = Boolean.parseBoolean(String.valueOf(raw.getOrDefault("inSync", true)));
        return new RewardPointsResponse(customerId, primaryPoints, source, inSync);
    }

    public RewardRedeemResponse redeem(String customerId, String transactionId, long points) {
        return posRewardClient.redeemPoints(new RewardRedeemRequest(customerId, transactionId, points));
    }

    /** Hoàn điểm đã redeem khi billing saga rollback (idempotent theo compensateTransactionId). */
    public RewardRedeemResponse compensateRedeem(String customerId, String compensateTransactionId, long points) {
        return posRewardClient.compensateRedeem(new RewardRedeemRequest(customerId, compensateTransactionId, points));
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
