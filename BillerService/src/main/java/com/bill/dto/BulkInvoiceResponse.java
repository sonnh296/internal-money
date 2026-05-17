package com.bill.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkInvoiceResponse {
    private int createdCount;
    private int skippedCount;
    private List<UUID> invoiceIds;
}
