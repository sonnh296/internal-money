package com.mockbank.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.mockbank.commons.dto.account.AccountResponse;
import com.mockbank.commons.dto.account.HoldResponse;
import com.mockbank.commons.dto.account.HoldStatus;
import com.mockbank.commons.dto.account.PostingRequest;
import com.mockbank.commons.dto.events.billpay.BillpayStatusEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockbank.payment.client.AccountM2MClient;
import com.mockbank.payment.client.BillerInvoiceM2MClient;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.domain.ProcessedEvent;
import com.mockbank.payment.repo.PaymentRepo;
import com.mockbank.payment.repo.ProcessedEventRepo;

class StatusConsumerTest {

    private PaymentRepo paymentRepo;
    private ProcessedEventRepo processedEventRepo;
    private ObjectMapper objectMapper;
    private AccountM2MClient accountM2MClient;
    private BillerInvoiceM2MClient billerInvoiceClient;
    private TransactionTemplate paymentTransactionTemplate;
    private BillPayCompensationService compensation;
    private StatusConsumer consumer;

    private List<PaymentState> recordSaveStates() {
        List<PaymentState> sequence = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            Payment saved = invocation.getArgument(0);
            sequence.add(saved.getState());
            return saved;
        }).when(paymentRepo).save(Mockito.any());
        return sequence;
    }

    @BeforeEach
    void setUp() {
        paymentRepo = Mockito.mock(PaymentRepo.class);
        processedEventRepo = Mockito.mock(ProcessedEventRepo.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        accountM2MClient = Mockito.mock(AccountM2MClient.class);
        billerInvoiceClient = Mockito.mock(BillerInvoiceM2MClient.class);
        compensation = Mockito.mock(BillPayCompensationService.class);
        paymentTransactionTemplate = Mockito.mock(TransactionTemplate.class);
        when(paymentTransactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });
        PaymentCompletionService completion = new PaymentCompletionService(
                paymentRepo, processedEventRepo, billerInvoiceClient, paymentTransactionTemplate);
        consumer = new StatusConsumer(paymentRepo, processedEventRepo, objectMapper, accountM2MClient,
                paymentTransactionTemplate, compensation, completion);
    }

    @Test
    void postedStatusShouldTransitionToCapturingBeforeCaptureAndThenPosted() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .debtorAccountId(accountId)
                .invoiceReference(invoiceId.toString())
                .amountValue(new BigDecimal("50.00"))
                .amountCcy("CAD")
                .executionDate(LocalDate.now())
                .state(PaymentState.FUNDS_HELD)
                .reason("ok")
                .idempotencyKey("idem-1")
                .build();

        BillpayStatusEvent event = new BillpayStatusEvent(eventId, paymentId, UUID.randomUUID(), "POSTED", "ok",
                OffsetDateTime.now());
        String message = objectMapper.writeValueAsString(event);

        when(processedEventRepo.existsByHandlerAndEventId("status", eventId.toString())).thenReturn(false);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment), Optional.of(payment), Optional.of(payment));
        when(accountM2MClient.captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class)))
                .thenReturn(new AccountResponse(accountId, "cust-123", "900000001", null, null, null, "CAD",
                        null, null, new BigDecimal("50.00"), null, 1, null, null));
        List<PaymentState> states = recordSaveStates();

        consumer.onMessage(message);

        assertEquals(PaymentState.CAPTURING, states.get(0));
        assertEquals(PaymentState.POSTED, states.get(states.size() - 1));

        InOrder order = inOrder(accountM2MClient, billerInvoiceClient);
        order.verify(accountM2MClient).captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class));
        order.verify(billerInvoiceClient).markPaid(invoiceId);
        verify(processedEventRepo).save(any(ProcessedEvent.class));
        verify(compensation, never()).releaseHoldAfterFailure(any(), any());
    }

    @Test
    void captureFailureShouldMarkFailedAndReleaseHold() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .debtorAccountId(accountId)
                .invoiceReference(UUID.randomUUID().toString())
                .amountValue(new BigDecimal("50.00"))
                .amountCcy("CAD")
                .executionDate(LocalDate.now())
                .state(PaymentState.FUNDS_HELD)
                .reason("ok")
                .idempotencyKey("idem-capture-fail")
                .build();

        BillpayStatusEvent event = new BillpayStatusEvent(eventId, paymentId, UUID.randomUUID(), "POSTED", "ok",
                OffsetDateTime.now());
        String message = objectMapper.writeValueAsString(event);

        when(processedEventRepo.existsByHandlerAndEventId("status", eventId.toString())).thenReturn(false);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment), Optional.of(payment), Optional.of(payment));
        when(accountM2MClient.captureHoldAndDebit(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("insufficient balance"));
        List<PaymentState> states = recordSaveStates();

        try {
            consumer.onMessage(message);
        } catch (RuntimeException ignored) {
        }

        assertEquals(PaymentState.CAPTURING, states.get(0));
        assertEquals(PaymentState.FAILED, states.get(1));
        verify(compensation).releaseHoldAfterFailure(accountId, paymentId);
        verify(billerInvoiceClient, never()).markPaid(any());
        verify(processedEventRepo, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void markPaidFailureAfterCaptureShouldNotReleaseHold() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .debtorAccountId(accountId)
                .invoiceReference(invoiceId.toString())
                .amountValue(new BigDecimal("50.00"))
                .amountCcy("CAD")
                .executionDate(LocalDate.now())
                .state(PaymentState.FUNDS_HELD)
                .reason("ok")
                .idempotencyKey("idem-mark-fail")
                .build();

        BillpayStatusEvent event = new BillpayStatusEvent(eventId, paymentId, UUID.randomUUID(), "POSTED", "ok",
                OffsetDateTime.now());
        String message = objectMapper.writeValueAsString(event);

        when(processedEventRepo.existsByHandlerAndEventId("status", eventId.toString())).thenReturn(false);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment), Optional.of(payment), Optional.of(payment));
        when(accountM2MClient.captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class)))
                .thenReturn(new AccountResponse(accountId, "cust-123", "900000001", null, null, null, "CAD",
                        null, null, new BigDecimal("50.00"), null, 1, null, null));
        Mockito.doThrow(new RuntimeException("biller down")).when(billerInvoiceClient).markPaid(invoiceId);
        List<PaymentState> states = recordSaveStates();

        try {
            consumer.onMessage(message);
        } catch (RuntimeException ignored) {
        }

        verify(accountM2MClient).captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class));
        verify(billerInvoiceClient).markPaid(invoiceId);
        verify(compensation, never()).releaseHoldAfterFailure(any(), any());
        assertEquals(PaymentState.CAPTURING, states.get(0));
        assertEquals(PaymentState.RECONCILIATION_REQUIRED, states.get(states.size() - 1));
        verify(processedEventRepo, never()).save(any(ProcessedEvent.class));
    }

    @Test
    void postedFromSubmittedShouldCaptureAndPost() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .debtorAccountId(accountId)
                .invoiceReference(invoiceId.toString())
                .amountValue(new BigDecimal("50.00"))
                .amountCcy("CAD")
                .executionDate(LocalDate.now())
                .state(PaymentState.SUBMITTED)
                .batchId(UUID.randomUUID())
                .reason("ok")
                .idempotencyKey("idem-submitted")
                .build();

        BillpayStatusEvent event = new BillpayStatusEvent(eventId, paymentId, UUID.randomUUID(), "POSTED", "ok",
                OffsetDateTime.now());
        String message = objectMapper.writeValueAsString(event);

        when(processedEventRepo.existsByHandlerAndEventId("status", eventId.toString())).thenReturn(false);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment), Optional.of(payment), Optional.of(payment));
        when(accountM2MClient.captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class)))
                .thenReturn(new AccountResponse(accountId, "cust-123", "900000001", null, null, null, "CAD",
                        null, null, new BigDecimal("50.00"), null, 1, null, null));
        List<PaymentState> states = recordSaveStates();

        consumer.onMessage(message);

        assertEquals(PaymentState.CAPTURING, states.get(0));
        assertEquals(PaymentState.POSTED, states.get(states.size() - 1));
        verify(accountM2MClient).captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class));
        verify(billerInvoiceClient).markPaid(invoiceId);
        verify(processedEventRepo).save(any(ProcessedEvent.class));
    }

    @Test
    void failedStatusShouldMarkPaymentFailed() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .debtorAccountId(accountId)
                .amountValue(new BigDecimal("50.00"))
                .amountCcy("CAD")
                .executionDate(LocalDate.now())
                .state(PaymentState.FUNDS_HELD)
                .build();

        BillpayStatusEvent event = new BillpayStatusEvent(eventId, paymentId, UUID.randomUUID(), "FAILED", "nack",
                OffsetDateTime.now());
        String message = objectMapper.writeValueAsString(event);

        when(processedEventRepo.existsByHandlerAndEventId("status", eventId.toString())).thenReturn(false);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment), Optional.of(payment));
        when(accountM2MClient.releaseHold(accountId, paymentId))
                .thenReturn(new HoldResponse(paymentId, new BigDecimal("50.00"), HoldStatus.RELEASED, LocalDateTime.now(), null));

        consumer.onMessage(message);

        verify(paymentRepo).save(any(Payment.class));
    }
}
