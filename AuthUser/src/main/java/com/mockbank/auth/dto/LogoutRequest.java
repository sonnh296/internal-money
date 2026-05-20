package com.mockbank.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {
    @NotBlank
    @JsonAlias("refresh_token")
    private String refreshToken;
}
