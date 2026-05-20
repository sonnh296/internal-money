package com.mockbank.auth.dto;
import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public  class CreateUserRequest {
        @Email
        @NotBlank
        private String email;
        @NotBlank
        @JsonAlias("password")
        private String temporaryPassword;
        @NotBlank
        private String customerId;
}