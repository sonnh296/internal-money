package com.mockbank.commons.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Cấu hình Security cho Microservice đóng vai trò là OAuth2 Resource Server.
 * Áp dụng mô hình zero-trust: mỗi service tự xác thực token thay vì phụ thuộc API Gateway.
 */
@Configuration
@EnableMethodSecurity
public class DefaultSecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuer;

    @Value("${auth.jwt.audience}")
    private String audience;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtToAuthConverter jwtToAuthConverter,
            CookieBearerFilter cookieBearerFilter) throws Exception {
        http
            .addFilterBefore(cookieBearerFilter, BearerTokenAuthenticationFilter.class)
            .csrf(cs -> cs.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/api/v1/health",
                    "/api/v1/customers/health",
                    "/api/v1/customer/register",
                    "/.well-known/jwks.json",
                    "/.well-known/openid-configuration",
                    "/api/v1/test/public",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/customers").permitAll()

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtToAuthConverter)
                )
            );

        return http.build();
    }

    @Bean
    public CookieBearerFilter cookieBearerFilter() {
        return new CookieBearerFilter();
    }

    @Bean
    public JwtToAuthConverter jwtToAuthConverter() {
        return new JwtToAuthConverter();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder dec = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuer);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);

        // Đảm bảo audience match với API của hệ thống
        OAuth2TokenValidator<Jwt> withAudience = token -> {
            Object aud = token.getClaims().get("aud");
            if (aud instanceof List && ((List<?>) aud).contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "missing/invalid audience", null)
            );
        };

        dec.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));

        return dec;
    }
}
