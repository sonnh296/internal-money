package com.mockbank.account.client;

import com.mockbank.commons.security.FeignTokenRelayConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "customer-service",
        url = "${customer.service.url}",
        configuration = FeignTokenRelayConfig.class
)
public interface CustomerServiceClient {

    @GetMapping("/api/v1/customers/{externalId}")
    CustomerProfileResponse getByExternalId(@PathVariable("externalId") String externalId);
}
