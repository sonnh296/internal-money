package com.payments.orch.client;

import com.payments.orch.security.FeignM2MOAuth2Config;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "biller-invoice-m2m",
        url = "${biller.service.url}",
        configuration = FeignM2MOAuth2Config.class
)
public interface BillerInvoiceM2MClient {

    @PatchMapping("/api/v1/internal/invoices/{id}/paid")
    void markPaid(@PathVariable("id") UUID id);
}
