package com.authuser.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.authuser.model.AuthUser;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {
    Optional<AuthUser> findByEmailIgnoreCase(String email);
    Optional<AuthUser> findByCustomerId(String customerId);
    List<AuthUser> findByRole(String role);
}
