package com.digitalbank.customerservice.service;

import org.springframework.stereotype.Service;

import com.commons.exception.ResourceNotFoundException;
import com.digitalbank.customerservice.dto.BalanceAdjustmentRequest;
import com.digitalbank.customerservice.repository.CustomerRepository;

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
