package com.mockbank.payment.client;

import java.util.UUID;

import com.mockbank.commons.security.FeignTokenRelayConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "registry", url = "${biller.service.url}", configuration = FeignTokenRelayConfig.class)
public interface BillerRegistryClient {
  @GetMapping("/api/v1/billers/{billerId}/active")
  boolean isActive(@PathVariable("billerId") String referenceNumber);
}
