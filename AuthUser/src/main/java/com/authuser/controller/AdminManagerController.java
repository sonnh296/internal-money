package com.authuser.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.authuser.dto.CreateManagerRequest;
import com.authuser.dto.ManagerResponse;
import com.authuser.model.AuthUser;
import com.authuser.service.InternalAuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/managers")
public class AdminManagerController {

    private final InternalAuthService internalAuthService;

    public AdminManagerController(InternalAuthService internalAuthService) {
        this.internalAuthService = internalAuthService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin:users.write')")
    public ResponseEntity<List<ManagerResponse>> listManagers() {
        List<ManagerResponse> managers = internalAuthService.listManagers()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(managers);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_admin:users.write')")
    public ResponseEntity<ManagerResponse> createManager(@Valid @RequestBody CreateManagerRequest request) {
        AuthUser created = internalAuthService.createManager(
                request.getEmail(), request.getCustomerId(), request.getTemporaryPassword());
        return ResponseEntity.ok(toResponse(created));
    }

    @DeleteMapping("/{id}/toggle")
    @PreAuthorize("hasAuthority('SCOPE_admin:users.write')")
    public ResponseEntity<Void> toggleEnabled(@PathVariable UUID id) {
        internalAuthService.toggleManagerEnabled(id);
        return ResponseEntity.noContent().build();
    }

    private ManagerResponse toResponse(AuthUser u) {
        return new ManagerResponse(
                u.getId(), u.getEmail(), u.getCustomerId(),
                u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
