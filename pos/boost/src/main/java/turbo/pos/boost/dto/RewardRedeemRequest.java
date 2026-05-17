package turbo.pos.boost.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RewardRedeemRequest {
    @NotBlank
    private String customerId;
    @NotBlank
    private String transactionId;
    @NotNull
    @Positive
    private Long points;
}
