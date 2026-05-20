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
public class DemoUserSeeder {

    @Bean
    CommandLineRunner seedDemoUser(
            AuthUserRepository authUserRepository,
            PasswordEncoder passwordEncoder,
            AuthJwtProperties jwtProperties,
            @Value("${app.demo-user.email:user.demo@mockbank.local}") String userEmail,
            @Value("${app.demo-user.password:User@12345}") String userPassword,
            @Value("${app.demo-user.customer-id:cust-demo-001}") String userCustomerId) {
        return args -> {
            String normalizedEmail = userEmail.trim().toLowerCase(Locale.ROOT);
            AuthUser user = authUserRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(AuthUser::new);
            user.setEmail(normalizedEmail);
            user.setCustomerId(userCustomerId);
            user.setPasswordHash(passwordEncoder.encode(userPassword));
            user.setPermissions(
                    "fdx:customers.read fdx:accounts.read fdx:accounts.write fdx:transactions.read fdx:bill.read fdx:bill.write fdx:payments.write");
            user.setEnabled(true);
            user.setRole("CUSTOMER");
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
            authUserRepository.save(user);
        };
    }
}
