package com.mockbank.biller.service;

import com.mockbank.biller.dto.SubscribeRequest;
import com.mockbank.biller.dto.SubscriptionResponse;
import com.mockbank.biller.model.ServicePackage;
import com.mockbank.biller.model.Subscription;
import com.mockbank.biller.repository.ServicePackageRepository;
import com.mockbank.biller.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepo;
    private final ServicePackageRepository packageRepo;

    public SubscriptionResponse subscribe(String customerId, SubscribeRequest req) {
        ServicePackage pkg = packageRepo.findById(req.getPackageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Service package not found"));
        if (!"ACTIVE".equals(pkg.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Service package is not active");
        }
        if (subscriptionRepo.existsByCustomerIdAndPackageId(customerId, req.getPackageId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already subscribed to this service");
        }
        Subscription sub = Subscription.builder()
                .customerId(customerId)
                .packageId(req.getPackageId())
                .status("ACTIVE")
                .build();
        return toDto(subscriptionRepo.save(sub), pkg);
    }

    public List<SubscriptionResponse> listForCustomer(String customerId) {
        return subscriptionRepo.findByCustomerId(customerId).stream()
                .map(sub -> {
                    ServicePackage pkg = packageRepo.findById(sub.getPackageId()).orElse(null);
                    return toDto(sub, pkg);
                })
                .collect(Collectors.toList());
    }

    public List<SubscriptionResponse> listIssuableSubscriptions() {
        return subscriptionRepo.findByStatus("ACTIVE").stream()
                .map(sub -> {
                    ServicePackage pkg = packageRepo.findById(sub.getPackageId()).orElse(null);
                    return toDto(sub, pkg);
                })
                .collect(Collectors.toList());
    }

    public void cancel(String customerId, java.util.UUID subscriptionId) {
        Subscription sub = subscriptionRepo.findById(subscriptionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        if (!sub.getCustomerId().equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your subscription");
        }
        sub.setStatus("CANCELLED");
        subscriptionRepo.save(sub);
    }

    private SubscriptionResponse toDto(Subscription sub, ServicePackage pkg) {
        return SubscriptionResponse.builder()
                .id(sub.getId())
                .customerId(sub.getCustomerId())
                .packageId(sub.getPackageId())
                .packageName(pkg != null ? pkg.getName() : null)
                .packageCategory(pkg != null ? pkg.getCategory() : null)
                .packageReferenceNumber(pkg != null ? pkg.getReferenceNumber() : null)
                .packageMonthlyAmount(pkg != null ? pkg.getMonthlyAmount() : null)
                .packageCurrency(pkg != null ? pkg.getCurrency() : null)
                .status(sub.getStatus())
                .createdAt(sub.getCreatedAt())
                .build();
    }
}
