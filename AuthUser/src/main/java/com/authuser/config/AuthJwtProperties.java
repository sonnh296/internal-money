package com.authuser.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {
    private String issuer;
    private String audience;
    private Duration accessTokenTtl = Duration.ofMinutes(30);
    private Duration refreshTokenTtl = Duration.ofDays(7);
    private String defaultPermissions = "fdx:customers.read fdx:accounts.read";
}
