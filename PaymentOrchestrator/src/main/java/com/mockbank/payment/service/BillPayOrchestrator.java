package com.mockbank.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockbank.payment.dto.BillPayRequest;
import com.mockbank.payment.dto.PaymentAcceptedResponse;
import com.mockbank.payment.client.AccountClient;
import com.mockbank.payment.client.AccountM2MClient;
import com.mockbank.commons.security.CurrentUser;
import com.mockbank.payment.domain.Outbox;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.repo.OutboxRepo;
import com.mockbank.payment.repo.PaymentRepo;
import com.mockbank.commons.dto.account.CreateHoldRequest;
import com.mockbank.commons.dto.events.billpay.BillPayRequested;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillPayOrchestrator {

  private final BillPayValidator validator;
  private final AccountClient accounts;
  private final AccountM2MClient accountM2MClient;
  private final CurrentUser currentUser;
  private final PaymentRepo paymentRepo;
  private final OutboxRepo outboxRepo;
  private final ObjectMapper om;
  private final TransactionTemplate paymentTransactionTemplate;
  private final BillPayCompensationService compensation;

  /** Không bọc HTTP/Feign trong @Transactional — chỉ persist payment + outbox trong TX ngắn. */
  public PaymentAcceptedResponse acceptBillPay(BillPayRequest req, String idemKey) {
    var existing = paymentRepo.findByIdempotencyKey(idemKey);
    if (existing.isPresent()) {
      var p = existing.get();
      return acceptedResponse(p);
    }

    validator.validate(req);

    BigDecimal holdAmount = req.amount().value();

    var holdReq = new CreateHoldRequest(
        holdAmount,
        "BILLPAY",
        null,
        idemKey);

    UUID holdId;
    try {
      holdId = placeHoldWithCircuitBreaker(req.debtorAccountId(), idemKey, holdReq);
    } catch (FeignException ex) {
      if (ex.status() == 409 || ex.status() == 422 || ex.status() == 400) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Account balance changed. Reload and retry payment.");
      }
      throw ex;
    }

    try {
      return paymentTransactionTemplate.execute(status ->
          persistPaymentAndOutbox(req, idemKey, holdId, holdAmount));
    } catch (RuntimeException ex) {
      compensation.releaseHoldAfterFailure(req.debtorAccountId(), holdId);
      throw ex;
    }
  }

  private PaymentAcceptedResponse persistPaymentAndOutbox(
      BillPayRequest req, String idemKey, UUID holdId, BigDecimal holdAmount) {
    var now = OffsetDateTime.now();
    var payment = Payment.builder()
        .paymentId(holdId)
        .state(PaymentState.FUNDS_HELD)
        .debtorAccountId(req.debtorAccountId())
        .billerRefNumber(req.billerReferenceNumber())
        .invoiceReference(req.invoiceReference())
        .executionDate(LocalDate.parse(req.executionDate()))
        .amountValue(holdAmount)
        .amountCcy(req.amount().currency())
        .reason(req.note())
        .idempotencyKey(idemKey)
        .createdAt(now)
        .updatedAt(now)
        .build();
    paymentRepo.save(payment);

    var evt = BillPayRequested.builder()
        .eventId(UUID.randomUUID().toString())
        .paymentId(holdId)
        .debtorAccountId(payment.getDebtorAccountId())
        .billerRefNumber(payment.getBillerRefNumber())
        .invoiceReference(payment.getInvoiceReference())
        .executionDate(payment.getExecutionDate().toString())
        .amountValue(payment.getAmountValue())
        .amountCcy(payment.getAmountCcy())
        .occurredAt(now.toString())
        .schemaVersion("1")
        .channel("billpay")
        .build();

    outboxRepo.save(Outbox.builder()
        .topic("billpay.requested")
        .key(holdId)
        .payloadJson(write(evt))
        .state("PENDING")
        .createdAt(now)
        .updatedAt(now)
        .build());

    return acceptedResponse(payment);
  }

  @CircuitBreaker(name = "accountClient")
  private UUID placeHoldWithCircuitBreaker(UUID accountId, String idemKey, CreateHoldRequest holdReq) {
    return accounts.placeHold(accountId, idemKey, holdReq).holdId();
  }

  public Payment view(UUID paymentId) {
    Payment p = paymentRepo.findById(paymentId).orElseThrow();
    
    // Validate ownership
    String ownerCustomerId = accountM2MClient.getOwner(p.getDebtorAccountId()).getCustomerId();
    String currentCustomerId = currentUser.customerId().orElse("");
    
    if (!ownerCustomerId.equals(currentCustomerId) && !currentUser.hasScope("admin:payments.read")) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized to view this payment");
    }
    
    return p;
  }

  private PaymentAcceptedResponse acceptedResponse(Payment p) {
    return new PaymentAcceptedResponse(
        p.getPaymentId(),
        p.getState().name(),
        "/api/v1/payments/" + p.getPaymentId());
  }

  private String write(Object o) {
    try {
      return om.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize outbox payload", e);
    }
  }
}
