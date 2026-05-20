package com.mockbank.account.management.repository;

import com.mockbank.commons.dto.account.HoldStatus;
import com.mockbank.account.management.model.AccountHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;



public interface AccountHoldRepository extends JpaRepository<AccountHold, UUID> {
    List<AccountHold> findByAccountIdAndStatus(UUID accountId, HoldStatus status);
    Optional<AccountHold> findByRequestFingerprint(String fingerprint);
    
    List<AccountHold> findByStatusAndReleaseAtLessThanEqual(HoldStatus status, LocalDateTime cutoff);

}