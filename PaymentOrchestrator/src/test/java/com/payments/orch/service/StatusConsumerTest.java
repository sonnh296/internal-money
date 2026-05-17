package com.payments.orch.service;

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
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.account.dto.AccountOwnerResponse;
import com.account.dto.AccountResponse;
import com.account.dto.HoldResponse;
import com.account.dto.HoldStatus;
import com.account.dto.PostingRequest;
import com.events.billpay.BillpayStatusEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.orch.client.AccountM2MClient;
import com.payments.orch.client.BillerInvoiceM2MClient;
import com.payments.orch.domain.Outbox;
import com.payments.orch.domain.Payment;
import com.payments.orch.domain.PaymentState;
import com.payments.orch.domain.ProcessedEvent;
import com.payments.orch.dto.RewardRedeemResponse;
import com.payments.orch.repo.OutboxRepo;
import com.payments.orch.repo.PaymentRepo;
import com.payments.orch.repo.ProcessedEventRepo;

class StatusConsumerTest {

    private PaymentRepo paymentRepo;
    private ProcessedEventRepo processedEventRepo;
    private ObjectMapper objectMapper;
    private AccountM2MClient accountM2MClient;
    private BillerInvoiceM2MClient billerInvoiceClient;
    private RewardService rewardService;
    private OutboxRepo outboxRepo;
    private TransactionTemplate paymentTransactionTemplate;
    private BillPayCompensationService compensation;
    private StatusConsumer consumer;

    @BeforeEach
    void setUp() {
        paymentRepo = Mockito.mock(PaymentRepo.class);
        processedEventRepo = Mockito.mock(ProcessedEventRepo.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        accountM2MClient = Mockito.mock(AccountM2MClient.class);
        billerInvoiceClient = Mockito.mock(BillerInvoiceM2MClient.class);
        rewardService = Mockito.mock(RewardService.class);
        outboxRepo = Mockito.mock(OutboxRepo.class);
        compensation = Mockito.mock(BillPayCompensationService.class);
        paymentTransactionTemplate = Mockito.mock(TransactionTemplate.class);
        when(paymentTransactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(Mockito.mock(TransactionStatus.class));
        });
        consumer = new StatusConsumer(paymentRepo, processedEventRepo, objectMapper, accountM2MClient, billerInvoiceClient,
                rewardService, outboxRepo, paymentTransactionTemplate, compensation);
        ReflectionTestUtils.setField(consumer, "paymentCompletedTopic", "payment.completed");
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
                .pointsToRedeem(0L)
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
        when(accountM2MClient.getOwner(accountId)).thenReturn(new AccountOwnerResponse(accountId, "cust-123"));

        consumer.onMessage(message);

        InOrder order = inOrder(paymentRepo, accountM2MClient, billerInvoiceClient);
        order.verify(paymentRepo).save(Mockito.argThat(p -> p.getState() == PaymentState.CAPTURING));
        order.verify(accountM2MClient).captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class));
        order.verify(billerInvoiceClient).markPaid(invoiceId);
        order.verify(paymentRepo).save(Mockito.argThat(p -> p.getState() == PaymentState.POSTED));
        verify(outboxRepo).save(any(Outbox.class));
        verify(processedEventRepo).save(any(ProcessedEvent.class));
        verify(compensation, never()).releaseHoldAfterFailure(any(), any());
    }

    @Test
    void postedStatusShouldCaptureHoldAndEnqueuePaymentCompletedOutbox() throws Exception {
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
                .pointsToRedeem(0L)
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
        when(accountM2MClient.getOwner(accountId)).thenReturn(new AccountOwnerResponse(accountId, "cust-123"));

        consumer.onMessage(message);

        verify(accountM2MClient).captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class));
        verify(accountM2MClient, never()).releaseHold(any(), any());
        verify(billerInvoiceClient).markPaid(invoiceId);
        verify(outboxRepo).save(any(Outbox.class));
        verify(paymentRepo, Mockito.atLeast(2)).save(any(Payment.class));
        verify(processedEventRepo).save(any(ProcessedEvent.class));
    }

    @Test
    void postedStatusShouldDebitFullAmountWhenRedeemFails() throws Exception {
        UUID paymentId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .debtorAccountId(accountId)
                .invoiceReference(UUID.randomUUID().toString())
                .amountValue(new BigDecimal("100.00"))
                .amountCcy("VND")
                .executionDate(LocalDate.now())
                .state(PaymentState.FUNDS_HELD)
                .reason("ok")
                .pointsToRedeem(500L)
                .idempotencyKey("idem-redeem-fail")
                .build();

        BillpayStatusEvent event = new BillpayStatusEvent(eventId, paymentId, UUID.randomUUID(), "POSTED", "ok",
                OffsetDateTime.now());
        String message = objectMapper.writeValueAsString(event);

        when(processedEventRepo.existsByHandlerAndEventId("status", eventId.toString())).thenReturn(false);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment), Optional.of(payment), Optional.of(payment));
        when(accountM2MClient.getOwner(accountId)).thenReturn(new AccountOwnerResponse(accountId, "cust-123"));
        when(rewardService.redeem(eq("cust-123"), eq("billpay:idem-redeem-fail"), eq(500L)))
                .thenReturn(new RewardRedeemResponse("cust-123", 0L, BigDecimal.ZERO, 0L, "LOCK_FAILED"));

        ArgumentCaptor<PostingRequest> postingCaptor = ArgumentCaptor.forClass(PostingRequest.class);
        when(accountM2MClient.captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                postingCaptor.capture()))
                .thenReturn(new AccountResponse(accountId, "cust-123", "900000001", null, null, null, "VND",
                        null, null, new BigDecimal("0.00"), null, 1, null, null));

        consumer.onMessage(message);

        assertEquals(new BigDecimal("100.00"), postingCaptor.getValue().amount());
        verify(accountM2MClient).captureHoldAndDebit(eq(accountId), eq(paymentId), eq(paymentId + ":DEBIT"),
                any(PostingRequest.class));
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
                .pointsToRedeem(0L)
                .idempotencyKey("idem-capture-fail")
                .build();

        BillpayStatusEvent event = new BillpayStatusEvent(eventId, paymentId, UUID.randomUUID(), "POSTED", "ok",
                OffsetDateTime.now());
        String message = objectMapper.writeValueAsString(event);

        when(processedEventRepo.existsByHandlerAndEventId("status", eventId.toString())).thenReturn(false);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment), Optional.of(payment), Optional.of(payment));
        when(accountM2MClient.getOwner(accountId)).thenReturn(new AccountOwnerResponse(accountId, "cust-123"));
        when(accountM2MClient.captureHoldAndDebit(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("insufficient balance"));

        try {
            consumer.onMessage(message);
        } catch (RuntimeException ignored) {
        }

        verify(paymentRepo).save(Mockito.argThat(p -> p.getState() == PaymentState.FAILED));
        verify(compensation).releaseHoldAfterFailure(accountId, paymentId);
        verify(outboxRepo, never()).save(any(Outbox.class));
        verify(billerInvoiceClient, never()).markPaid(any());
    }

    @Test
    void failedStatusShouldMarkPaymentFailedWithoutRewardEvent() throws Exception {
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

        verify(outboxRepo, never()).save(any(Outbox.class));
        verify(paymentRepo).save(any(Payment.class));
    }
}
