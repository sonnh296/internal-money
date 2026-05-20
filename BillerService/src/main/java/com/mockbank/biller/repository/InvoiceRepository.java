package com.mockbank.biller.repository;

import com.mockbank.biller.model.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByCustomerId(String customerId);
    List<Invoice> findByCustomerIdAndStatus(String customerId, String status);
    Page<Invoice> findByCustomerIdAndStatus(String customerId, String status, Pageable pageable);
    List<Invoice> findBySubscriptionId(UUID subscriptionId);
    boolean existsByCustomerIdAndPackageIdAndDueDateAndStatus(
            String customerId, UUID packageId, java.time.LocalDate dueDate, String status);
}
