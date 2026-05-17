package com.payments.orch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.orch.client.AccountClient;
import com.payments.orch.dto.AmountDto;
import com.payments.orch.dto.BillPayRequest;
import com.payments.orch.repo.OutboxRepo;
import com.payments.orch.repo.PaymentRepo;

import feign.FeignException;
import feign.Request;
import feign.Request.HttpMethod;
import feign.Response;

class BillPayOrchestratorTest {

    private BillPayValidator validator;
    private AccountClient accountClient;
    private PaymentRepo paymentRepo;
    private OutboxRepo outboxRepo;
    private TransactionTemplate paymentTransactionTemplate;
    private BillPayCompensationService compensation;
    private BillPayOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        validator = Mockito.mock(BillPayValidator.class);
        accountClient = Mockito.mock(AccountClient.class);
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
                "test",
                0L);

        when(paymentRepo.findByIdempotencyKey("idem-001")).thenReturn(Optional.empty());
        when(accountClient.placeHold(eq(accountId), eq("idem-001"), any()))
                .thenThrow(feignConflict());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orchestrator.acceptBillPay(request, "idem-001"));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Account balance changed. Reload and retry payment.", ex.getReason());
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
