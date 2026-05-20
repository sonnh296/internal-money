package com.mockbank.biller.controller;

import com.mockbank.biller.dto.ServicePackageRequest;
import com.mockbank.biller.dto.ServicePackageResponse;
import com.mockbank.biller.service.ServicePackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServicePackageController {

    private final ServicePackageService service;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<ServicePackageResponse> create(@RequestBody ServicePackageRequest req) {
        ServicePackageResponse res = service.create(req);
        return ResponseEntity.created(URI.create("/api/v1/services/" + res.getId())).body(res);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.read')")
    public ResponseEntity<List<ServicePackageResponse>> list(
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        List<ServicePackageResponse> result = all ? service.listAll() : service.listActive(limit, offset);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.read')")
    public ResponseEntity<ServicePackageResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<ServicePackageResponse> toggleStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(service.toggleStatus(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_fdx:bill.write')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
