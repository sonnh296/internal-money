package com.mockbank.biller.repository;

import com.mockbank.biller.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByCustomerId(String customerId);
    List<Subscription> findByStatus(String status);
    Optional<Subscription> findByCustomerIdAndPackageId(String customerId, UUID packageId);
    List<Subscription> findByPackageIdAndStatus(UUID packageId, String status);
    boolean existsByCustomerIdAndPackageId(String customerId, UUID packageId);
}
