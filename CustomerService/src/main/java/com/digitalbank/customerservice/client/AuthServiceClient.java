package com.digitalbank.customerservice.client;

import com.digitalbank.customerservice.dto.CustomerRegistrationRequest;
import com.commons.security.FeignTokenRelayConfig;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@FeignClient(
        name = "auth-service",
        url = "${auth.service.url}",
        configuration = FeignTokenRelayConfig.class
)
public interface AuthServiceClient {
    @PostMapping("/api/v1/internal/users")
    void registerCustomer(@RequestBody CustomerRegistrationRequest request);
}