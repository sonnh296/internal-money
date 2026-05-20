package com.mockbank.biller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServicePackageRequest {
    private String name;
    private String category;
    private String referenceNumber;
    private BigDecimal monthlyAmount;
    private String currency;
    private String description;
}
