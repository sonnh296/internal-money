package com.bill.repository;

import com.bill.model.ServicePackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ServicePackageRepository extends JpaRepository<ServicePackage, UUID> {
    Page<ServicePackage> findByStatus(String status, Pageable pageable);
    Optional<ServicePackage> findByReferenceNumber(String referenceNumber);
    boolean existsByReferenceNumber(String referenceNumber);

    boolean existsByReferenceNumberAndStatus(String referenceNumber, String status);
}
