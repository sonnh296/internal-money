package com.digitalbank.customerservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CustomerRegistrationRequest {
    private String email;
    @JsonAlias("password")
    private String temporaryPassword;
    private String customerId; // Link to customer-service record
}
