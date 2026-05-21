package com.mockbank.commons.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Converter chuyển đổi JWT từ Auth0 thành Spring Security AbstractAuthenticationToken.
 * Bóc tách `permissions` (RBAC) và `scope` để tạo danh sách authority với prefix `SCOPE_`,
 * hỗ trợ phân quyền bằng `@PreAuthorize("hasAuthority('SCOPE_...')")`.
 */
@Component
public class JwtToAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * Chuyển đổi JWT decode thành JwtAuthenticationToken.
     * Được gọi tự động bởi Spring Security.
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {

        Set<String> scopes = new LinkedHashSet<>();

        // 1. Trích xuất claim permissions (được Auth0 thêm vào khi bật RBAC)
        Object perms = jwt.getClaims().get("permissions");
        if (perms instanceof Collection<?>) {
            for (Object p : (Collection<?>) perms) {
                scopes.add(String.valueOf(p));
            }
        }

        // 2. Trích xuất claim scope (ngăn cách bởi dấu cách)
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            scopes.addAll(Arrays.asList(scope.split(" ")));
        }

        // 3. Chuyển đổi thành Spring authorities với prefix SCOPE_
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (final String s : scopes) {
            authorities.add(() -> "SCOPE_" + s);
        }

        return new JwtAuthenticationToken(jwt, authorities);
    }
}
