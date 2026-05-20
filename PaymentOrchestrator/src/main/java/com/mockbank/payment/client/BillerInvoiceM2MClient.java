package com.mockbank.payment.client;

import com.mockbank.commons.security.feign.FeignM2MOAuth2Config;
import com.mockbank.payment.client.dto.BillerInvoiceSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "biller-invoice-m2m",
        url = "${biller.service.url}",
        configuration = FeignM2MOAuth2Config.class
)
public interface BillerInvoiceM2MClient {

    @GetMapping("/api/v1/internal/invoices/{id}")
    BillerInvoiceSnapshot getInvoice(@PathVariable("id") UUID id);

    @PatchMapping("/api/v1/internal/invoices/{id}/paid")
    void markPaid(@PathVariable("id") UUID id);
}
