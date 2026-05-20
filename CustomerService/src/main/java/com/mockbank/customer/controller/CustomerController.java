package com.mockbank.customer.controller;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mockbank.customer.dto.BalanceAdjustmentRequest;
import com.mockbank.customer.dto.CustomerCreatedResponse;
import com.mockbank.customer.dto.CustomerRequest;
import com.mockbank.customer.dto.CustomerResponse;
import com.mockbank.customer.dto.UpdateCustomerRequest;
import com.mockbank.customer.dto.UpdateKycStatusRequest;
import com.mockbank.customer.service.CustomerNotificationService;
import com.mockbank.customer.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;
    private final CustomerNotificationService notificationService;

    @PostMapping("/customers")
    public ResponseEntity<CustomerCreatedResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
    	CustomerCreatedResponse body = service.create(request);
    	  URI loc = URI.create("/api/v1/customers/" + body.getExternalId());
    	  return ResponseEntity.created(loc).body(body);
    }

    
    @GetMapping("/admin/customers")
    @PreAuthorize("hasAuthority('SCOPE_admin:users.write') or hasAuthority('SCOPE_fdx:customers.read')")
    public ResponseEntity<java.util.List<CustomerResponse>> listAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(service.listAll(page, size));
    }

    @GetMapping("/customers/{externalId}")
    @PreAuthorize("hasAuthority('SCOPE_fdx:customers.read')")
    public ResponseEntity<CustomerResponse> getCustomerByExternalId(@PathVariable String externalId) {
        CustomerResponse dto = service.getByExternalId(externalId);
        var builder = ResponseEntity.ok();
        if (dto.getVersion() != null) {
            builder.eTag("\"" + dto.getVersion() + "\"");
        }
        return builder.body(dto);
    }
    
    
    @GetMapping("/customers/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Customer Service is up");
    }


    @GetMapping("/customers/exists")
    @PreAuthorize("hasAuthority('SCOPE_fdx:customers.read')")
    public ResponseEntity<Boolean> existsByEmail(@RequestParam String email) {
        return ResponseEntity.ok(service.existsByEmail(email));

    }
    
    @GetMapping("/customers/{externalId}/exists")
    @PreAuthorize("hasAuthority('SCOPE_fdx:customers.read')")
    public boolean exists(@PathVariable String externalId) {
        return service.exists(externalId);
    }
    
    
    @PatchMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('SCOPE_admin:users.write')")
    public ResponseEntity<Void> updateCustomer(
            @PathVariable String id,
            @RequestHeader(name = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateCustomerRequest request) {

        Integer expected = parseIfMatch(ifMatch);
        Integer newVersion = service.updateCustomer(id, request, expected);

        // ✅ Return 200 OK, no body, only ETag
        return ResponseEntity.ok()
                .eTag("\"" + newVersion + "\"")
                .build();
    }

    @PreAuthorize("hasAuthority('SCOPE_admin:users.write')")
    @PatchMapping("/customers/{id}/kyc-status")
    public ResponseEntity<Void> updateKycStatus(
            @PathVariable String id,
            @RequestBody UpdateKycStatusRequest request) {

        Integer newVersion = service.updateKycStatus(id, request.getKycStatus());

        // ✅ Return 204 No Content, only ETag
        return ResponseEntity.noContent()
                .eTag("\"" + newVersion + "\"")
                .build();
    }

    @PostMapping("/admin/notifications/balance-adjustment")
    @PreAuthorize("hasAnyAuthority('SCOPE_admin:accounts','SCOPE_admin:accounts.write')")
    public ResponseEntity<Void> notifyBalanceAdjustment(@Valid @RequestBody BalanceAdjustmentRequest request) {
        notificationService.notifyBalanceAdjustment(request);
        return ResponseEntity.accepted().build();
    }
    private Integer parseIfMatch(String ifMatch) {
      if (ifMatch == null || ifMatch.isBlank()) return null;
      // Accept bare numbers (e.g. 3) or quoted ("3")
      String v = ifMatch.replace("\"", "").trim();
      return Integer.valueOf(v);
    }
    
}