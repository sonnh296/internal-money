package turbo.pos.boost.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import turbo.pos.boost.dto.RewardRedeemRequest;
import turbo.pos.boost.dto.RewardRedeemResponse;
import turbo.pos.boost.repository.RewardRepository;

@Service
@RequiredArgsConstructor
public class RewardRedeemService {

    private static final BigDecimal POINTS_PER_CURRENCY = new BigDecimal("10");
    private static final String AMOUNT_KEY = "amount";
    private static final String POINTS_DELTA_KEY = "points_delta";
    private static final String CUSTOMER_ID_KEY = "customer_id";
    private final RewardRepository rewardRepository;

    @Transactional
    public RewardRedeemResponse redeem(RewardRedeemRequest request) {
        if (rewardRepository.existsByTransactionId(request.getTransactionId())) {
            return duplicateResponse(request);
        }

        rewardRepository.ensureCustomerBalanceRecord(request.getCustomerId());
        long balance = rewardRepository.findBalanceByCustomerIdForUpdate(request.getCustomerId()).orElse(0L);
        if (request.getPoints() > balance) {
            return RewardRedeemResponse.builder()
                    .customerId(request.getCustomerId())
                    .redeemedPoints(0)
                    .redeemedAmount(BigDecimal.ZERO)
                    .remainingPoints(balance)
                    .status("INSUFFICIENT_POINTS")
                    .build();
        }

        BigDecimal redeemedAmount = new BigDecimal(request.getPoints())
                .divide(POINTS_PER_CURRENCY, 2, RoundingMode.DOWN);
        rewardRepository.insertLedgerEntry(
                request.getCustomerId(),
                request.getTransactionId(),
                redeemedAmount.negate(),
                -request.getPoints());
        long newBalance = balance - request.getPoints();
        rewardRepository.updateBalance(request.getCustomerId(), newBalance);

        return RewardRedeemResponse.builder()
                .customerId(request.getCustomerId())
                .redeemedPoints(request.getPoints())
                .redeemedAmount(redeemedAmount)
                .remainingPoints(newBalance)
                .status("SUCCESS")
                .build();
    }

    private RewardRedeemResponse duplicateResponse(RewardRedeemRequest request) {
        long remaining = rewardRepository.findBalanceByCustomerId(request.getCustomerId()).orElse(0L);
        return rewardRepository.findLedgerByTransactionId(request.getTransactionId())
                .map(this::mapDuplicate)
                .orElseGet(() -> RewardRedeemResponse.builder()
                        .customerId(request.getCustomerId())
                        .redeemedPoints(0)
                        .redeemedAmount(BigDecimal.ZERO)
                        .remainingPoints(remaining)
                        .status("DUPLICATE_TRANSACTION")
                        .build());
    }

    /**
     * Hoàn điểm khi billing saga rollback — idempotent theo compensateTransactionId.
     */
    @Transactional
    public RewardRedeemResponse compensate(RewardRedeemRequest request) {
        if (rewardRepository.existsByTransactionId(request.getTransactionId())) {
            return duplicateResponse(request);
        }

        rewardRepository.ensureCustomerBalanceRecord(request.getCustomerId());
        long balance = rewardRepository.findBalanceByCustomerIdForUpdate(request.getCustomerId()).orElse(0L);
        BigDecimal restoreAmount = new BigDecimal(request.getPoints())
                .divide(POINTS_PER_CURRENCY, 2, RoundingMode.DOWN);
        rewardRepository.insertLedgerEntry(
                request.getCustomerId(),
                request.getTransactionId(),
                restoreAmount,
                request.getPoints());
        long newBalance = balance + request.getPoints();
        rewardRepository.updateBalance(request.getCustomerId(), newBalance);

        return RewardRedeemResponse.builder()
                .customerId(request.getCustomerId())
                .redeemedPoints(request.getPoints())
                .redeemedAmount(restoreAmount)
                .remainingPoints(newBalance)
                .status("COMPENSATED")
                .build();
    }

    private RewardRedeemResponse mapDuplicate(Map<String, Object> row) {
        String customerId = String.valueOf(row.get(CUSTOMER_ID_KEY));
        long pointsDelta = ((Number) row.getOrDefault(POINTS_DELTA_KEY, 0)).longValue();
        BigDecimal amount = row.get(AMOUNT_KEY) instanceof BigDecimal bigDecimal
                ? bigDecimal
                : new BigDecimal(String.valueOf(row.getOrDefault(AMOUNT_KEY, "0")));
        long remaining = rewardRepository.findBalanceByCustomerId(customerId).orElse(0L);

        return RewardRedeemResponse.builder()
                .customerId(customerId)
                .redeemedPoints(Math.max(0L, -pointsDelta))
                .redeemedAmount(amount.abs())
                .remainingPoints(remaining)
                .status("DUPLICATE_TRANSACTION")
                .build();
    }
}
