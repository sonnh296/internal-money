package com.mockbank.auth.config;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.mockbank.auth.model.AuthUser;
import com.mockbank.auth.repository.AuthUserRepository;

@Configuration
@Profile("dev")
public class DemoAdminSeeder {

    @Bean
    CommandLineRunner seedDemoAdmin(
            AuthUserRepository authUserRepository,
            PasswordEncoder passwordEncoder,
            AuthJwtProperties jwtProperties,
            @Value("${app.demo-admin.email:admin.demo@mockbank.local}") String adminEmail,
            @Value("${app.demo-admin.password:Admin@12345}") String adminPassword,
            @Value("${app.demo-admin.customer-id:admin-root-0001}") String adminCustomerId) {
        return args -> {
            String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
            AuthUser user = authUserRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(AuthUser::new);
            user.setEmail(normalizedEmail);
            user.setCustomerId(adminCustomerId);
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setPermissions(jwtProperties.getDefaultPermissions());
            user.setEnabled(true);
            user.setRole("SUPER_ADMIN");
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            authUserRepository.save(user);
        };
    }
}
