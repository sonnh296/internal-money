package com.mockbank.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockbank.auth.dto.ChangePasswordRequest;
import com.mockbank.auth.dto.LoginRequest;
import com.mockbank.auth.dto.LogoutRequest;
import com.mockbank.auth.dto.RefreshTokenRequest;
import com.mockbank.auth.dto.TokenResponse;
import com.mockbank.auth.service.InternalAuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final InternalAuthService internalAuthService;

    public AuthController(InternalAuthService internalAuthService) {
        this.internalAuthService = internalAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(internalAuthService.login(request.getEmail(), request.getPassword()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(internalAuthService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        internalAuthService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<TokenResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(internalAuthService.changePassword(
                jwt.getSubject(), request.getCurrentPassword(), request.getNewPassword()));
    }
}
