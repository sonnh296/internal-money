package com.mockbank.payment.client;

import java.util.UUID;

import com.mockbank.commons.dto.account.AccountOwnerResponse;
import com.mockbank.commons.dto.account.AccountResponse;
import com.mockbank.commons.dto.account.HoldResponse;
import com.mockbank.commons.dto.account.PostingRequest;

import jakarta.validation.Valid;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
        name = "account-servicem2m",
        url = "${account.service.url}",         
        configuration = com.mockbank.commons.security.feign.FeignM2MOAuth2Config.class
)
public interface AccountM2MClient {

   
	  @GetMapping("/api/v1/accounts/{accountId}/holds/{holdId}")
	  HoldResponse getHold(
	      @PathVariable("accountId") UUID accountId,
	      @PathVariable("holdId") UUID holdId
	  );

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
