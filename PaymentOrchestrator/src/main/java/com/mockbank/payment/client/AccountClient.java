package com.mockbank.payment.client;

import java.util.UUID;

import com.mockbank.commons.dto.account.AccountResponse;
import com.mockbank.commons.dto.account.CreateHoldRequest;
import com.mockbank.commons.dto.account.HoldResponse;
import com.mockbank.commons.dto.account.PostingRequest;
import com.mockbank.commons.security.FeignTokenRelayConfig;

import jakarta.validation.Valid;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "account-service",
        url = "${account.service.url}",         
        configuration = FeignTokenRelayConfig.class  
)
public interface AccountClient {

    @PostMapping("/api/v1/accounts/{id}/holds")
    HoldResponse placeHold(
            @PathVariable("id") UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateHoldRequest request
    );

    @PostMapping("/api/v1/accounts/{id}/holds/{holdId}/release")
    HoldResponse releaseHold(
            @PathVariable("id") UUID id,
            @PathVariable("holdId") UUID holdId);
}
