package com.mockbank.biller.controller;

import com.mockbank.biller.dto.InvoiceResponse;
import com.mockbank.biller.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/invoices")
@RequiredArgsConstructor
public class InternalInvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getById(id));
    }

    @PatchMapping("/{id}/paid")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<InvoiceResponse> markPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.markPaid(id));
    }
}
