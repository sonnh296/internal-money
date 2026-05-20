package com.mockbank.commons.dto.account;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import lombok.*;



public record AccountBalanceResponse(
        BigDecimal balance,
        BigDecimal totalHolds,
        BigDecimal available
) {}