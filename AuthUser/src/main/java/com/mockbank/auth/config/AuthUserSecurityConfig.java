package com.mockbank.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class AuthUserSecurityConfig {

    @Bean
    public com.mockbank.commons.security.CookieBearerFilter cookieBearerFilter() {
        return new com.mockbank.commons.security.CookieBearerFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, com.mockbank.commons.security.CookieBearerFilter cookieBearerFilter)
            throws Exception {
        http
            .addFilterBefore(cookieBearerFilter, BearerTokenAuthenticationFilter.class)
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/api/v1/test/public",
                    "/actuator/health",
                    "/.well-known/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> {}));

        return http.build();
    }
}
