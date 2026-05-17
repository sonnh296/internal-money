package com.payments.orch.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.commons.exception.UpstreamException;
import com.payments.orch.client.AccountClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bù trừ (compensating) khi bước sau thất bại: giải phóng hold.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillPayCompensationService {

	private static final int MAX_RELEASE_ATTEMPTS = 3;
	private static final long RELEASE_BACKOFF_MS = 200L;

	private final AccountClient accountClient;

	/**
	 * Giải phóng hold với retry — không nuốt lỗi để tránh khóa tiền khách vĩnh viễn.
	 */
	public void releaseHoldAfterFailure(UUID accountId, UUID holdId) {
		if (accountId == null || holdId == null) {
			return;
		}
		for (int attempt = 1; attempt <= MAX_RELEASE_ATTEMPTS; attempt++) {
			try {
				accountClient.releaseHold(accountId, holdId);
				log.info("Compensated: released hold accountId={} holdId={}", accountId, holdId);
				return;
			} catch (Exception ex) {
				if (attempt >= MAX_RELEASE_ATTEMPTS) {
					log.error("Compensation failed after {} attempts: accountId={} holdId={}",
							attempt, accountId, holdId, ex);
					throw new UpstreamException(
							"Không thể giải phóng hold sau lỗi thanh toán. holdId=" + holdId
									+ " accountId=" + accountId);
				}
				log.warn("Compensation retry {}/{} for holdId={}: {}", attempt, MAX_RELEASE_ATTEMPTS, holdId,
						ex.getMessage());
				sleepBackoff(attempt);
			}
		}
	}

	private static void sleepBackoff(int attempt) {
		try {
			Thread.sleep(RELEASE_BACKOFF_MS * attempt);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new UpstreamException("Compensation interrupted while releasing hold");
		}
	}

}
