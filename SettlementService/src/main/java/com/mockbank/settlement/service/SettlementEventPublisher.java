package com.mockbank.settlement.service;

import com.mockbank.commons.dto.events.billpay.BillBatchSubmittedEvent;
import com.mockbank.commons.dto.events.billpay.BillBatchRetryEvent;
import com.mockbank.commons.dto.events.billpay.BillpayStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.mockbank.settlement.domain.Outbox;
import com.mockbank.settlement.repo.OutboxRepo;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementEventPublisher {

    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;   // Jackson from Spring Boot

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize {} event to JSON", event.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to serialize event", e);
        }
    }

    public void publishBatchSubmitted(BillBatchSubmittedEvent event) {
        log.info("Emitting bill.batch.submitted for batchId={}", event.batchId());
        saveOutbox("bill.batch.submitted", event.batchId().toString(), toJson(event));
    }

    public void publishDlq(UUID batchId, String error) {
        log.info("Emitting bill.batch.dlq for batchId={} error={}", batchId, error);

        BillBatchRetryEvent event = new BillBatchRetryEvent(
                batchId,
                4,
                error,
                OffsetDateTime.now()
        );

        saveOutbox("bill.batch.dlq", batchId.toString(), toJson(event));
    }

    public void publishBillpayStatus(BillpayStatusEvent event) {
        log.info("Emitting billpay.status for paymentId={} batchId={} status={}",
                event.paymentId(), event.batchId(), event.status());

        saveOutbox("billpay.status", event.paymentId().toString(), toJson(event));
    }

    public void publishBatchRetry(BillBatchRetryEvent event) {
        log.info("Emitting bill.batch.retry for batchId={} attempt={}",
                event.batchId(), event.attemptNumber());

        saveOutbox("bill.batch.retry", event.batchId().toString(), toJson(event));
    }
    private void saveOutbox(String topic, String key, String payload) {
        Outbox outbox = Outbox.builder()
                .topic(topic)
                .key(key)
                .payloadJson(payload)
                .state("PENDING")
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        outboxRepo.save(outbox);
    }
}
