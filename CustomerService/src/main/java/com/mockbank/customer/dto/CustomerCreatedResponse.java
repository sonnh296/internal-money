// com.mockbank.customer.dto.CustomerCreatedResponse
package com.mockbank.customer.dto;

import java.time.LocalDateTime;
import com.mockbank.customer.model.KycStatus;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomerCreatedResponse {
    private String externalId;
    private KycStatus kycStatus;
    private Integer version;
    private LocalDateTime createdAt;
}
