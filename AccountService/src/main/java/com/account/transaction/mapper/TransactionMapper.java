package com.account.transaction.mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.account.dto.AmountDTO;
import com.account.dto.TransactionRequest;
import com.account.dto.TransactionResponse;
import com.account.transaction.model.Transaction;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    // Request -> Entity: names mostly match; BigDecimal -> BigDecimal auto maps.
    Transaction toEntity(TransactionRequest request);

    @Mappings({
        @Mapping(target = "id",     expression = "java(entity.getTransactionId())"),
        @Mapping(target = "type",   expression = "java(entity.getType() != null ? entity.getType().name() : null)"),
        @Mapping(target = "status", expression = "java(entity.getStatus() != null ? entity.getStatus().name() : null)")
    })
    TransactionResponse toResponse(Transaction entity);
    // Helper
    default AmountDTO toAmountDTO(BigDecimal value) {
        if (value == null) return null;
        return AmountDTO.builder().currency("VND").value(value).build();
    }
}
