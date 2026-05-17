package com.authuser.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.authuser.config.AuthJwtProperties;
import com.authuser.config.AuthSecurityProperties;
import com.authuser.dto.TokenResponse;
import com.authuser.model.AuthUser;
import com.authuser.model.RefreshToken;
import com.authuser.repository.AuthUserRepository;
import com.authuser.repository.RefreshTokenRepository;

@Service
public class InternalAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_LOGIN_ERROR = "Invalid credentials";

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthJwtProperties jwtProperties;
    private final AuthSecurityProperties authSecurityProperties;

    public InternalAuthService(
            AuthUserRepository authUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            AuthJwtProperties jwtProperties,
            AuthSecurityProperties authSecurityProperties) {
        this.authUserRepository = authUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.authSecurityProperties = authSecurityProperties;
    }

    @Transactional
    public AuthUser createInternalUser(String email, String customerId, String temporaryPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return authUserRepository.findByEmailIgnoreCase(normalizedEmail)
                .map(existing -> updateExistingUser(existing, customerId, temporaryPassword))
                .orElseGet(() -> createNewUser(normalizedEmail, customerId, temporaryPassword));
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        AuthUser user = authUserRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR));

        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not active");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "User temporarily locked");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            registerFailedLogin(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, GENERIC_LOGIN_ERROR);
        }

        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        authUserRepository.save(user);

        return issueTokenPair(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SESSION_INVALIDATED");
        }
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        return issueTokenPair(refreshToken.getUser());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public TokenResponse changePassword(String userIdStr, String currentPassword, String newPassword) {
        AuthUser user = authUserRepository.findById(java.util.UUID.fromString(userIdStr))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        authUserRepository.save(user);
        refreshTokenRepository.findByUserAndRevoked(user, false)
                .forEach(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
        return issueTokenPair(user);
    }

    @Transactional
    public AuthUser createManager(String email, String customerId, String temporaryPassword) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (authUserRepository.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        AuthUser user = new AuthUser();
        user.setEmail(normalized);
        user.setCustomerId(customerId);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setEnabled(true);
        user.setRole("MANAGER");
        // Manager gets read-only admin scopes
        user.setPermissions("fdx:customers.read fdx:accounts.read admin:accounts.read admin:accounts.write fdx:bill.read");
        return authUserRepository.save(user);
    }

    public java.util.List<AuthUser> listManagers() {
        return authUserRepository.findByRole("MANAGER");
    }

    @Transactional
    public void toggleManagerEnabled(java.util.UUID id) {
        AuthUser user = authUserRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manager not found"));
        if (!"MANAGER".equals(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a manager account");
        }
        user.setEnabled(!user.isEnabled());
        authUserRepository.save(user);
    }

    private AuthUser createNewUser(String normalizedEmail, String customerId, String temporaryPassword) {
        AuthUser user = new AuthUser();
        user.setEmail(normalizedEmail);
        user.setCustomerId(customerId);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setEnabled(true);
        user.setRole("CUSTOMER");
        user.setPermissions(jwtProperties.getDefaultPermissions());
        return authUserRepository.save(user);
    }

    private AuthUser updateExistingUser(AuthUser user, String customerId, String temporaryPassword) {
        user.setCustomerId(customerId);
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setEnabled(true);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        return authUserRepository.save(user);
    }

    private void registerFailedLogin(AuthUser user) {
        int failures = user.getFailedLoginCount() + 1;
        user.setFailedLoginCount(failures);
        if (failures >= authSecurityProperties.getMaxFailedAttempts()) {
            user.setLockedUntil(LocalDateTime.now().plus(authSecurityProperties.getLockDuration()));
            user.setFailedLoginCount(0);
        }
        authUserRepository.save(user);
    }

    private TokenResponse issueTokenPair(AuthUser user) {
        List<String> permissions = parsePermissions(user.getPermissions());
        String accessToken = jwtTokenService.createAccessToken(user, permissions);
        String refreshTokenValue = generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(refreshTokenValue);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(LocalDateTime.now().plus(jwtProperties.getRefreshTokenTtl()));
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .expiresIn(jwtProperties.getAccessTokenTtl().toSeconds())
                .tokenType("Bearer")
                .build();
    }

    private static List<String> parsePermissions(String permissions) {
        if (permissions == null || permissions.isBlank()) {
            return List.of();
        }
        return Arrays.stream(permissions.split("[,\\s]+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static String generateRefreshToken() {
        byte[] randomBytes = new byte[48];
        RANDOM.nextBytes(randomBytes);
        return UUID.nameUUIDFromBytes(randomBytes).toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }
}
