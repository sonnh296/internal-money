package com.mockbank.customer.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.mockbank.customer.model.KycStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerResponse {
	 private UUID id;
	    private String firstName;
	    private String lastName;
	    private String email;
	    private String phone;
	    private String address;
	    private String externalId;
	    private Boolean active;
	    private KycStatus kycStatus;
	    private LocalDateTime createdAt;
	    private LocalDateTime updatedAt;
	    private Integer version;
}