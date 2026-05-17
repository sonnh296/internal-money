package com.account.dto;
import java.math.BigDecimal;
import java.util.UUID;


public record AccountResponse(
        UUID id,
        String customerId,
        String accountNumber,
        AccountType accountType,
        AccountSubType accountSubType,
        AccountStatus status,
        String currency,
        String nickname,
        String displayName,
        BigDecimal balance,
        String maskedAccountNumber,
        Integer version,
        /** Số dư khả dụng = balance − tổng hold ACTIVE (null nếu chưa tính). */
        BigDecimal availableBalance,
        /** Tổng tiền đang bị giữ (hold) cho thanh toán đang xử lý. */
        BigDecimal totalHolds
) {}