package com.authuser.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateManagerRequest {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String customerId;

    @NotBlank
    private String temporaryPassword;
}
