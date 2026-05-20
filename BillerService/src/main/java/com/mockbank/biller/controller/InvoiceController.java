package com.mockbank.biller.controller;

import com.mockbank.commons.security.CurrentUser;
import com.mockbank.biller.dto.BulkInvoiceResponse;
import com.mockbank.biller.dto.CreateInvoiceRequest;
import com.mockbank.biller.dto.InvoiceResponse;

import jakarta.validation.Valid;
import com.mockbank.biller.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final CurrentUser currentUser;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<BulkInvoiceResponse> create(@Valid @RequestBody CreateInvoiceRequest req) {
        BulkInvoiceResponse res = invoiceService.createForPackage(req);
        return ResponseEntity.created(URI.create("/api/v1/invoices")).body(res);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.read')")
    public ResponseEntity<List<InvoiceResponse>> listAll() {
        return ResponseEntity.ok(invoiceService.listAll());
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.read')")
    public ResponseEntity<Page<InvoiceResponse>> listMine(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        String customerId = currentUser.customerId().get();
        return ResponseEntity.ok(invoiceService.listPendingForCustomer(customerId, limit, offset));
    }

    @PatchMapping("/{id}/paid")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<InvoiceResponse> markPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.markPaid(id));
    }
}
