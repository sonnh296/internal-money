package com.mockbank.payment.controller;

import com.mockbank.payment.dto.BillPayRequest;
import com.mockbank.payment.dto.PaymentAcceptedResponse;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.service.BillPayOrchestrator;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

  private final BillPayOrchestrator billPayOrchestrator;

  @PostMapping("/payments/billpay")
  @RateLimiter(name = "billpay")
  public ResponseEntity<PaymentAcceptedResponse> billPay(
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody BillPayRequest req
  ) {
    var res = billPayOrchestrator.acceptBillPay(req, idempotencyKey);
    var location = URI.create("/api/v1/payments/" + res.paymentId());
    return ResponseEntity.accepted().location(location).body(res);
  }

  @GetMapping("/payments/{paymentId}")
  public ResponseEntity<Payment> get(@PathVariable UUID paymentId) {
    return ResponseEntity.ok(billPayOrchestrator.view(paymentId));
  }
}
