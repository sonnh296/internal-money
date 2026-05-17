package com.bill.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateInvoiceRequest {
    @NotNull(message = "packageId is required")
    private UUID packageId;
    private BigDecimal amount;
    @NotNull(message = "dueDate is required")
    @FutureOrPresent(message = "dueDate must be today or in the future")
    private LocalDate dueDate;
}
