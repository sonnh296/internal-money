package com.authuser.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManagerResponse {
    private UUID id;
    private String email;
    private String customerId;
    private String role;
    private boolean enabled;
    private LocalDateTime createdAt;
}
