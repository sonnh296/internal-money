package com.mockbank.customer.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockbank.customer.dto.BalanceAdjustmentRequest;
import com.mockbank.customer.service.CustomerNotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/internal/notifications")
@RequiredArgsConstructor
public class InternalNotificationController {

    private final CustomerNotificationService notificationService;

    @PostMapping("/balance-adjustment")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<Void> balanceAdjustment(@Valid @RequestBody BalanceAdjustmentRequest request) {
        notificationService.notifyBalanceAdjustment(request);
        return ResponseEntity.accepted().build();
    }
}
