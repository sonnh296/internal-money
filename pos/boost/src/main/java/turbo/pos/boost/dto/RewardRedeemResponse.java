package turbo.pos.boost.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardRedeemResponse {
    private String customerId;
    private long redeemedPoints;
    private BigDecimal redeemedAmount;
    private long remainingPoints;
    private String status;
}
