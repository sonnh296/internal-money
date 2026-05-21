package com.mockbank.auth.service;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.mockbank.auth.config.AuthCookieProperties;
import com.mockbank.auth.dto.TokenResponse;

@Service
public class AuthCookieService {

  public static final String PORTAL_USER = "user";
  public static final String PORTAL_ADMIN = "admin";

  private final AuthCookieProperties properties;

  public AuthCookieService(AuthCookieProperties properties) {
    this.properties = properties;
  }

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public void addAuthCookies(HttpHeaders headers, String portal, TokenResponse tokens) {
    if (!properties.isEnabled()) {
      return;
    }
    String prefix = cookiePrefix(portal);
    headers.add(HttpHeaders.SET_COOKIE, accessCookie(prefix, tokens).toString());
    headers.add(HttpHeaders.SET_COOKIE, refreshCookie(prefix, tokens).toString());
  }

  public void clearAuthCookies(HttpHeaders headers, String portal) {
    if (!properties.isEnabled()) {
      return;
    }
    String prefix = cookiePrefix(portal);
    headers.add(HttpHeaders.SET_COOKIE, clearCookie(prefix + "_access").toString());
    headers.add(HttpHeaders.SET_COOKIE, clearCookie(prefix + "_refresh").toString());
  }

  public static String cookiePrefix(String portal) {
    return PORTAL_ADMIN.equalsIgnoreCase(portal) ? "bp_admin" : "bp_user";
  }

  private ResponseCookie accessCookie(String prefix, TokenResponse tokens) {
    return baseCookie(prefix + "_access", tokens.getAccessToken(),
        Duration.ofSeconds(Math.max(tokens.getExpiresIn(), 60L)));
  }

  private ResponseCookie refreshCookie(String prefix, TokenResponse tokens) {
    return baseCookie(prefix + "_refresh", tokens.getRefreshToken(), Duration.ofDays(7));
  }

  private ResponseCookie baseCookie(String name, String value, Duration maxAge) {
    return ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(properties.isSecure())
        .sameSite(properties.getSameSite())
        .path(properties.getPath())
        .maxAge(maxAge)
        .build();
  }

  private ResponseCookie clearCookie(String name) {
    return ResponseCookie.from(name, "")
        .httpOnly(true)
        .secure(properties.isSecure())
        .sameSite(properties.getSameSite())
        .path(properties.getPath())
        .maxAge(0)
        .build();
  }
}
