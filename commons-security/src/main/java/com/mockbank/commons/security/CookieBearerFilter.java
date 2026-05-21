package com.mockbank.commons.security;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Đọc JWT từ HttpOnly cookie (bp_*_access) và gắn Authorization Bearer
 * trước khi OAuth2 Resource Server xác thực.
 */
public class CookieBearerFilter extends OncePerRequestFilter {

    private static final String PORTAL_HEADER = "X-Portal";
    private static final String COOKIE_USER_ACCESS = "bp_user_access";
    private static final String COOKIE_ADMIN_ACCESS = "bp_admin_access";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (StringUtils.hasText(request.getHeader("Authorization"))) {
            chain.doFilter(request, response);
            return;
        }
        String portal = request.getHeader(PORTAL_HEADER);
        String cookieName = "admin".equalsIgnoreCase(portal) ? COOKIE_ADMIN_ACCESS : COOKIE_USER_ACCESS;
        String token = readCookie(request, cookieName);
        if (!StringUtils.hasText(token)) {
            chain.doFilter(request, response);
            return;
        }
        String bearer = "Bearer " + token;
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("Authorization".equalsIgnoreCase(name)) {
                    return bearer;
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if ("Authorization".equalsIgnoreCase(name)) {
                    return Collections.enumeration(List.of(bearer));
                }
                return super.getHeaders(name);
            }
        };
        chain.doFilter(wrapped, response);
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
