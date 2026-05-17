package com.bill.controller;

import com.commons.security.CurrentUser;
import com.bill.dto.BulkInvoiceResponse;
import com.bill.dto.CreateInvoiceRequest;
import com.bill.dto.InvoiceResponse;

import jakarta.validation.Valid;
import com.bill.service.InvoiceService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<InvoiceResponse>> listMine() {
        String customerId = currentUser.customerId().get();
        return ResponseEntity.ok(invoiceService.listPendingForCustomer(customerId));
    }

    @PatchMapping("/{id}/paid")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<InvoiceResponse> markPaid(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.markPaid(id));
    }
}
