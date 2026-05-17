package com.bill.controller;

import com.commons.security.CurrentUser;
import com.bill.dto.SubscribeRequest;
import com.bill.dto.SubscriptionResponse;
import com.bill.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CurrentUser currentUser;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<SubscriptionResponse> subscribe(@RequestBody SubscribeRequest req) {
        String customerId = currentUser.customerId().get();
        SubscriptionResponse res = subscriptionService.subscribe(customerId, req);
        return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + res.getId())).body(res);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.read')")
    public ResponseEntity<List<SubscriptionResponse>> listMine() {
        String customerId = currentUser.customerId().get();
        return ResponseEntity.ok(subscriptionService.listForCustomer(customerId));
    }

    @GetMapping("/admin/issuable")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:users.write','SCOPE_fdx:bill.read')")
    public ResponseEntity<List<SubscriptionResponse>> listIssuableForAdmin() {
        return ResponseEntity.ok(subscriptionService.listIssuableSubscriptions());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        String customerId = currentUser.customerId().get();
        subscriptionService.cancel(customerId, id);
        return ResponseEntity.noContent().build();
    }
}
