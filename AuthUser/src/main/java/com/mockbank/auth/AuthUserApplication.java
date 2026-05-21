package com.mockbank.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import com.mockbank.auth.config.AuthCookieProperties;
import com.mockbank.commons.security.FeignTokenRelayConfig;

@SpringBootApplication
@EnableConfigurationProperties(AuthCookieProperties.class)
@Import({FeignTokenRelayConfig.class})
public class AuthUserApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthUserApplication.class, args);
	}
}
