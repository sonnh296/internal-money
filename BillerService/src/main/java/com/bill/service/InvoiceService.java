package com.bill.service;

import com.bill.dto.BulkInvoiceResponse;
import com.bill.dto.CreateInvoiceRequest;
import com.bill.dto.InvoiceResponse;
import com.bill.model.Invoice;
import com.bill.model.ServicePackage;
import com.bill.model.Subscription;
import com.bill.repository.InvoiceRepository;
import com.bill.repository.ServicePackageRepository;
import com.bill.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepo;
    private final SubscriptionRepository subscriptionRepo;
    private final ServicePackageRepository packageRepo;

    public BulkInvoiceResponse createForPackage(CreateInvoiceRequest req) {
        ServicePackage pkg = packageRepo.findById(req.getPackageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Package not found"));
        if (req.getDueDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Due date is required");
        }
        if (req.getAmount() != null && req.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be zero or positive");
        }

        List<Subscription> activeSubs = subscriptionRepo.findByPackageIdAndStatus(req.getPackageId(), "ACTIVE");
        if (activeSubs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No active subscriptions found for this package");
        }

        int created = 0;
        int skipped = 0;
        List<UUID> invoiceIds = new ArrayList<>();

        for (Subscription sub : activeSubs) {
            if (invoiceRepo.existsByCustomerIdAndPackageIdAndDueDateAndStatus(
                    sub.getCustomerId(), req.getPackageId(), req.getDueDate(), "PENDING")) {
                skipped++;
                continue;
            }
            BigDecimal computedAmount = req.getAmount() != null
                    ? req.getAmount()
                    : standardInvoiceAmount(pkg);
            Invoice inv = Invoice.builder()
                    .subscriptionId(sub.getId())
                    .customerId(sub.getCustomerId())
                    .packageId(sub.getPackageId())
                    .billerReferenceNumber(pkg.getReferenceNumber())
                    .amount(computedAmount)
                    .currency(pkg.getCurrency())
                    .dueDate(req.getDueDate())
                    .status("PENDING")
                    .build();
            Invoice saved = invoiceRepo.save(inv);
            invoiceIds.add(saved.getId());
            created++;
        }

        return BulkInvoiceResponse.builder()
                .createdCount(created)
                .skippedCount(skipped)
                .invoiceIds(invoiceIds)
                .build();
    }

    public List<InvoiceResponse> listAll() {
        return invoiceRepo.findAll().stream()
                .map(inv -> {
                    ServicePackage pkg = packageRepo.findById(inv.getPackageId()).orElse(null);
                    return toDto(inv, pkg);
                })
                .collect(Collectors.toList());
    }

    public List<InvoiceResponse> listForCustomer(String customerId) {
        return invoiceRepo.findByCustomerId(customerId).stream()
                .map(inv -> {
                    ServicePackage pkg = packageRepo.findById(inv.getPackageId()).orElse(null);
                    return toDto(inv, pkg);
                })
                .collect(Collectors.toList());
    }

    public List<InvoiceResponse> listPendingForCustomer(String customerId) {
        return invoiceRepo.findByCustomerIdAndStatus(customerId, "PENDING").stream()
                .map(inv -> {
                    ServicePackage pkg = packageRepo.findById(inv.getPackageId()).orElse(null);
                    return toDto(inv, pkg);
                })
                .collect(Collectors.toList());
    }

    public InvoiceResponse markPaid(UUID id) {
        Invoice inv = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));
        if ("PAID".equals(inv.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice already paid");
        }
        inv.setStatus("PAID");
        ServicePackage pkg = packageRepo.findById(inv.getPackageId()).orElse(null);
        return toDto(invoiceRepo.save(inv), pkg);
    }

    private InvoiceResponse toDto(Invoice inv, ServicePackage pkg) {
        return InvoiceResponse.builder()
                .id(inv.getId())
                .subscriptionId(inv.getSubscriptionId())
                .customerId(inv.getCustomerId())
                .packageId(inv.getPackageId())
                .packageName(pkg != null ? pkg.getName() : null)
                .billerReferenceNumber(inv.getBillerReferenceNumber())
                .amount(inv.getAmount())
                .currency(inv.getCurrency())
                .dueDate(inv.getDueDate())
                .status(inv.getStatus())
                .createdAt(inv.getCreatedAt())
                .build();
    }

    /** Mọi khách hàng trả cùng một mức phí gói, không phụ thuộc ngày đăng ký. */
    private BigDecimal standardInvoiceAmount(ServicePackage pkg) {
        if (pkg.getMonthlyAmount() == null || pkg.getMonthlyAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Gói dịch vụ chưa có mức phí hợp lệ");
        }
        return pkg.getMonthlyAmount().setScale(2, RoundingMode.HALF_UP);
    }
}
