package com.payments.orch.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.payments.orch.client.PosRewardClient;
import com.payments.orch.dto.PosRewardRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosRewardSyncService {

    private final PosRewardClient posRewardClient;

    @Value("${pos.reward-sync.max-attempts:3}")
    private int maxAttempts;

    @Value("${pos.reward-sync.backoff-ms:250}")
    private long backoffMs;

    public boolean syncReward(String customerId, UUID paymentId, BigDecimal amount) {
        PosRewardRequest request = new PosRewardRequest(customerId, paymentId.toString(), amount);
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            try {
                posRewardClient.processReward(request);
                return true;
            } catch (Exception ex) {
                log.warn("POS reward sync failed for paymentId={} attempt={}/{}: {}", paymentId, attempt, maxAttempts,
                        ex.getMessage());
                if (attempt < maxAttempts) {
                    sleepBackoff();
                }
            }
        }
        return false;
    }

    /**
     * Gọi POS reward bất đồng bộ trong thread pool riêng.
     * Không block Kafka consumer thread — reward là best-effort side-effect.
     */
    @Async("rewardSyncExecutor")
    public CompletableFuture<Boolean> syncRewardAsync(String customerId, UUID paymentId, BigDecimal amount) {
        boolean result = syncReward(customerId, paymentId, amount);
        if (!result) {
            log.warn("Reward sync thất bại sau tất cả retry cho paymentId={}", paymentId);
        }
        return CompletableFuture.completedFuture(result);
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(Math.max(0, backoffMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
