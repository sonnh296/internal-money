package com.mockbank.auth.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthProfileResponse(
    String email,
    @JsonProperty("customer_id") String customerId,
    List<String> scopes,
    String role) {
}
