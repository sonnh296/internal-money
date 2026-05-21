package com.mockbank.auth.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mockbank.auth.dto.AuthProfileResponse;
import com.mockbank.auth.dto.ChangePasswordRequest;
import com.mockbank.auth.dto.LoginRequest;
import com.mockbank.auth.dto.LogoutRequest;
import com.mockbank.auth.dto.RefreshTokenRequest;
import com.mockbank.auth.dto.TokenResponse;
import com.mockbank.auth.repository.AuthUserRepository;
import com.mockbank.auth.service.AuthCookieService;
import com.mockbank.auth.service.InternalAuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final InternalAuthService internalAuthService;
    private final AuthCookieService authCookieService;
    private final AuthUserRepository authUserRepository;

    public AuthController(
            InternalAuthService internalAuthService,
            AuthCookieService authCookieService,
            AuthUserRepository authUserRepository) {
        this.internalAuthService = internalAuthService;
        this.authCookieService = authCookieService;
        this.authUserRepository = authUserRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(
            @RequestHeader(value = "X-Portal", defaultValue = AuthCookieService.PORTAL_USER) String portal,
            @Valid @RequestBody LoginRequest request) {
        TokenResponse tokens = internalAuthService.login(request.getEmail(), request.getPassword());
        HttpHeaders headers = new HttpHeaders();
        authCookieService.addAuthCookies(headers, portal, tokens);
        return ResponseEntity.ok().headers(headers).body(maskTokensIfCookieMode(tokens));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @RequestHeader(value = "X-Portal", defaultValue = AuthCookieService.PORTAL_USER) String portal,
            @RequestBody(required = false) RefreshTokenRequest request,
            @CookieValue(value = "bp_user_refresh", required = false) String userRefresh,
            @CookieValue(value = "bp_admin_refresh", required = false) String adminRefresh) {
        String refreshValue = resolveRefreshToken(portal, request, userRefresh, adminRefresh);
        if (refreshValue == null || refreshValue.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Refresh token required");
        }
        TokenResponse tokens = internalAuthService.refresh(refreshValue);
        HttpHeaders headers = new HttpHeaders();
        authCookieService.addAuthCookies(headers, portal, tokens);
        return ResponseEntity.ok().headers(headers).body(maskTokensIfCookieMode(tokens));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-Portal", defaultValue = AuthCookieService.PORTAL_USER) String portal,
            @RequestBody(required = false) LogoutRequest request,
            @CookieValue(value = "bp_user_refresh", required = false) String userRefresh,
            @CookieValue(value = "bp_admin_refresh", required = false) String adminRefresh) {
        String refreshValue = resolveRefreshToken(portal, request, userRefresh, adminRefresh);
        if (refreshValue != null && !refreshValue.isBlank()) {
            internalAuthService.logout(refreshValue);
        }
        HttpHeaders headers = new HttpHeaders();
        authCookieService.clearAuthCookies(headers, portal);
        return ResponseEntity.noContent().headers(headers).build();
    }

    @GetMapping("/me")
    public AuthProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        String email = authUserRepository.findById(UUID.fromString(jwt.getSubject()))
                .map(u -> u.getEmail())
                .orElse(jwt.getSubject());
        return new AuthProfileResponse(
                email,
                jwt.getClaimAsString("customer_id"),
                extractScopes(jwt),
                jwt.getClaimAsString("role"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<TokenResponse> changePassword(
            @RequestHeader(value = "X-Portal", defaultValue = AuthCookieService.PORTAL_USER) String portal,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        TokenResponse tokens = internalAuthService.changePassword(
                jwt.getSubject(), request.getCurrentPassword(), request.getNewPassword());
        HttpHeaders headers = new HttpHeaders();
        authCookieService.addAuthCookies(headers, portal, tokens);
        return ResponseEntity.ok().headers(headers).body(maskTokensIfCookieMode(tokens));
    }

    private TokenResponse maskTokensIfCookieMode(TokenResponse tokens) {
        if (!authCookieService.isEnabled()) {
            return tokens;
        }
        return TokenResponse.builder()
                .accessToken("")
                .refreshToken("")
                .expiresIn(tokens.getExpiresIn())
                .tokenType(tokens.getTokenType())
                .build();
    }

    private static String resolveRefreshToken(
            String portal,
            RefreshTokenRequest request,
            String userRefresh,
            String adminRefresh) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken();
        }
        if (AuthCookieService.PORTAL_ADMIN.equalsIgnoreCase(portal)) {
            return adminRefresh;
        }
        return userRefresh;
    }

    private static String resolveRefreshToken(
            String portal,
            LogoutRequest request,
            String userRefresh,
            String adminRefresh) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            return request.getRefreshToken();
        }
        if (AuthCookieService.PORTAL_ADMIN.equalsIgnoreCase(portal)) {
            return adminRefresh;
        }
        return userRefresh;
    }

    private static List<String> extractScopes(Jwt jwt) {
        List<String> scopes = new ArrayList<>();
        Object permissions = jwt.getClaim("permissions");
        if (permissions instanceof List<?> list) {
            for (Object p : list) {
                scopes.add(String.valueOf(p));
            }
        }
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) {
            for (String s : scope.split(" ")) {
                if (!s.isBlank()) {
                    scopes.add(s);
                }
            }
        }
        return scopes;
    }
}
