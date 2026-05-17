package com.payments.orch.client;

import java.util.UUID;

import com.account.dto.AccountOwnerResponse;
import com.account.dto.AccountResponse;
import com.account.dto.HoldResponse;
import com.account.dto.PostingRequest;

import jakarta.validation.Valid;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "account-servicem2m",
        url = "${account.service.url}",         
        configuration = com.payments.orch.security.FeignM2MOAuth2Config.class
)
public interface AccountM2MClient {

   
	  @PostMapping("/api/v1/accounts/{accountId}/holds/{holdId}/release")
	  HoldResponse releaseHold(
	      @PathVariable("accountId") UUID accountId,
	      @PathVariable("holdId") UUID holdId
	  );

	  @PostMapping("/api/v1/accounts/{accountId}/holds/{holdId}/capture")
	  AccountResponse captureHoldAndDebit(
	      @PathVariable("accountId") UUID accountId,
	      @PathVariable("holdId") UUID holdId,
	      @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
	      @Valid @RequestBody PostingRequest request
	  );

	  @GetMapping("/api/v1/accounts/{id}/owner")
	  AccountOwnerResponse getOwner(@PathVariable("id") UUID id);
}
