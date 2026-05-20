package com.mockbank.commons.dto.account;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public  class AmountDTO {
        private String currency;
        private BigDecimal value;
    }
