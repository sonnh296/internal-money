package turbo.pos.boost.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RewardUtils {

    /** Số điểm thưởng cho mỗi 1 đơn vị tiền tệ (đồng) */
    public static final long REWARD_POINTS_PER_CURRENCY_UNIT = 10L;

    private RewardUtils() {}

    /**
     * Tính điểm thưởng dựa trên số tiền giao dịch (không dùng double để tránh sai số làm tròn).
     */
    public static long calculatePoints(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.setScale(0, RoundingMode.DOWN).longValue() * REWARD_POINTS_PER_CURRENCY_UNIT;
    }
}
