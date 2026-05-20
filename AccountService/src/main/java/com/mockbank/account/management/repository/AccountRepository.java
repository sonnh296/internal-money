package com.mockbank.account.management.repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mockbank.account.management.model.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByCustomerId(String customerId);
    Optional<Account> findFirstByCustomerId(String customerId);
    Optional<Account> findByRequestFingerprint(String fingerprint);
    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
