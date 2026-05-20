package com.mockbank.customer.client;

import com.mockbank.commons.dto.account.AccountResponse;
import com.mockbank.commons.dto.account.ProvisionAccountRequest;
import com.mockbank.commons.security.feign.FeignM2MOAuth2Config;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "account-service-m2m",
        url = "${account.service.url}",
        configuration = FeignM2MOAuth2Config.class
)
public interface AccountServiceClient {

    @PostMapping("/api/v1/internal/accounts/provision")
    AccountResponse provisionAccount(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ProvisionAccountRequest request);
}
