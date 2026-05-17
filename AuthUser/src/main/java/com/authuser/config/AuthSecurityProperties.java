package com.authuser.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth.security")
public class AuthSecurityProperties {
    private int maxFailedAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);
}
