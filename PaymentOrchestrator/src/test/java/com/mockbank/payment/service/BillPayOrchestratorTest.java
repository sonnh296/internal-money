package com.mockbank.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mockbank.commons.dto.account.AccountOwnerResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockbank.commons.security.CurrentUser;
import com.mockbank.payment.client.AccountClient;
import com.mockbank.payment.client.AccountM2MClient;
import com.mockbank.payment.dto.AmountDto;
import com.mockbank.payment.dto.BillPayRequest;
import com.mockbank.payment.repo.OutboxRepo;
import com.mockbank.payment.repo.PaymentRepo;

import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;

class BillPayOrchestratorTest {

    private BillPayValidator validator;
    private AccountClient accountClient;
    private AccountM2MClient accountM2MClient;
    private CurrentUser currentUser;
    private PaymentRepo paymentRepo;
    private OutboxRepo outboxRepo;
    private TransactionTemplate paymentTransactionTemplate;
    private BillPayCompensationService compensation;
    private BillPayOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        validator = Mockito.mock(BillPayValidator.class);
        accountClient = Mockito.mock(AccountClient.class);
        accountM2MClient = Mockito.mock(AccountM2MClient.class);
        currentUser = Mockito.mock(CurrentUser.class);
        paymentRepo = Mockito.mock(PaymentRepo.class);
        outboxRepo = Mockito.mock(OutboxRepo.class);
        paymentTransactionTemplate = Mockito.mock(TransactionTemplate.class);
        compensation = Mockito.mock(BillPayCompensationService.class);

        when(paymentTransactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });

        orchestrator = new BillPayOrchestrator(
                validator,
                accountClient,
                accountM2MClient,
                currentUser,
                paymentRepo,
                outboxRepo,
                new ObjectMapper(),
                paymentTransactionTemplate,
                compensation);
    }

    @Test
    void acceptBillPayShouldMapAccountInsufficientFundsTo409() {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        BillPayRequest request = new BillPayRequest(
                accountId,
                "BILLER-REF-001",
                "INV-001",
                LocalDate.now().plusDays(1).toString(),
                new AmountDto(new java.math.BigDecimal("100.00"), "VND"),
                "test");

        when(paymentRepo.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(accountClient.placeHold(eq(accountId), eq("idem-001"), any()))
                .thenThrow(feignConflict());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orchestrator.acceptBillPay(request, "idem-001"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Account balance changed. Reload and retry payment.", ex.getReason());
    }

    @Test
    void persistRaceShouldReplayWithoutReleasingHold() {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        UUID holdId = UUID.fromString("00000000-0000-0000-0000-000000000222");
        BillPayRequest request = new BillPayRequest(
                accountId,
                "BILLER-REF-001",
                "INV-001",
                LocalDate.now().plusDays(1).toString(),
                new AmountDto(new java.math.BigDecimal("100.00"), "VND"),
                "test");

        Payment existing = Payment.builder()
                .paymentId(holdId)
                .state(PaymentState.FUNDS_HELD)
                .debtorAccountId(accountId)
                .billerRefNumber("BILLER-REF-001")
                .invoiceReference("INV-001")
                .executionDate(LocalDate.now().plusDays(1))
                .amountValue(new java.math.BigDecimal("100.00"))
                .amountCcy("VND")
                .idempotencyKey("idem-race")
                .build();

        when(paymentRepo.findByIdempotencyKey("idem-race")).thenReturn(Optional.empty(), Optional.of(existing));
        when(currentUser.hasScope(Mockito.anyString())).thenReturn(true);
        when(accountClient.placeHold(eq(accountId), eq("idem-race"), any()))
                .thenReturn(new com.mockbank.commons.dto.account.HoldResponse(
                        holdId, new java.math.BigDecimal("100.00"),
                        com.mockbank.commons.dto.account.HoldStatus.ACTIVE,
                        java.time.LocalDateTime.now(), null));
        when(paymentTransactionTemplate.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        var response = orchestrator.acceptBillPay(request, "idem-race");

        assertEquals(holdId, response.paymentId());
        assertEquals("FUNDS_HELD", response.state());
        verify(compensation, never()).releaseHoldAfterFailure(any(), any());
    }

    private FeignException feignConflict() {
        Request req = Request.create(
                HttpMethod.POST,
                "/api/v1/accounts/holds",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .request(req)
                .status(409)
                .reason("Conflict")
                .headers(Map.of())
                .body("insufficient", StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("AccountClient#placeHold", response);
    }
}
