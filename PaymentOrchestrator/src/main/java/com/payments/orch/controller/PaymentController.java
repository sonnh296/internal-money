package com.payments.orch.controller;

import com.payments.orch.dto.BillPayRequest;
import com.payments.orch.dto.PaymentAcceptedResponse;
import com.payments.orch.dto.RewardPointsResponse;
import com.payments.orch.domain.Payment;
import com.payments.orch.service.BillPayOrchestrator;
import com.payments.orch.service.RewardService;
import com.commons.security.CurrentUser;
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
  private final RewardService rewardService;
  private final CurrentUser currentUser;

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

  @GetMapping("/rewards/me")
  public ResponseEntity<RewardPointsResponse> myRewards() {
    String customerId = currentUser.customerId()
        .orElseThrow(() -> new IllegalArgumentException("CUSTOMER_ID_CLAIM_MISSING"));
    return ResponseEntity.ok(rewardService.getPoints(customerId));
  }
}
