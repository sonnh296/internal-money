package com.mockbank.customer.service;

import org.springframework.stereotype.Service;

import com.mockbank.commons.dto.exception.ResourceNotFoundException;
import com.mockbank.customer.dto.BalanceAdjustmentRequest;
import com.mockbank.customer.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomerNotificationService {

    private final CustomerRepository repository;
    private final EmailNotificationService emailNotificationService;

    public void notifyBalanceAdjustment(BalanceAdjustmentRequest request) {
        var customer = repository.findByExternalId(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.customerId()));
        emailNotificationService.sendBalanceAdjustment(
                customer.getEmail(),
                customer.getFirstName(),
                request.amount(),
                request.type(),
                request.reason(),
                request.balanceAfter());
    }
}
