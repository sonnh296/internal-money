package com.mockbank.billpay.worker.service;

import com.mockbank.commons.dto.events.EventSchemaSupport;
import com.mockbank.billpay.worker.domain.Batch;
import com.mockbank.billpay.worker.domain.BatchLine;
import com.mockbank.commons.dto.events.billpay.*;
import com.mockbank.billpay.worker.mocksettlement.Central1Simulator;
import com.mockbank.billpay.worker.repo.BatchLineRepository;
import com.mockbank.billpay.worker.repo.BatchRepository;
import com.mockbank.billpay.worker.repo.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillPayWorkerService {

    private final BatchRepository batches;
    private final BatchLineRepository lines;
    private final OutboxRepository outboxRepository;
    private final Central1Simulator central1Simulator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${payments.topics.billpay-enqueued:billpay.enqueued}")
    private String enqueuedTopic;

    @Value("${payments.topics.bill-batch-ready:bill.batch.ready}")
    private String batchReadyTopic;

    @Value("${payments.batch.threshold:1}")
    private int batchThreshold;

    @Transactional
    public void handleRequested(BillPayRequested evt) {
        EventSchemaSupport.requireVersion1(evt.getSchemaVersion(), "BillPayRequested");
        log.info("Handling BillPayRequested paymentId={} correlationId={}", evt.getPaymentId(), evt.getCorrelationId());

        // 1) find or create OPEN batch
        Batch batch = batches.findFirstByStatusOrderByCreatedAtAsc("OPEN")
                .orElseGet(() -> {
                    UUID batchId = UUID.randomUUID();
                    Batch b = Batch.builder()
                            .batchId(batchId)
                            .status("OPEN")
                            .createdAt(OffsetDateTime.now())
                            .build();
                    batches.save(b);
                    log.info("Opened new batch {}", batchId);
                    return b;
                });

        long currentLines = lines.countByBatchId(batch.getBatchId());
        int nextLineNo = (int) currentLines + 1;

    
        BatchLine line = BatchLine.builder()
                .batchId(batch.getBatchId())
                .paymentId(evt.getPaymentId())
                .lineNo(nextLineNo)
                .createdAt(OffsetDateTime.now())
                .build();
        lines.save(line);

        log.info("Enqueued payment {} into batch {} lineNo={}", evt.getPaymentId(), batch.getBatchId(), nextLineNo);

        // 2) Emit billpay.enqueued via Outbox
        BillPayEnqueued enq = BillPayEnqueued.builder()
                .eventId(UUID.randomUUID().toString())
                .paymentId(evt.getPaymentId())
                .batchId(batch.getBatchId())
                .correlationId(evt.getCorrelationId())
                .occurredAt(OffsetDateTime.now().toString())
                .schemaVersion("1")
                .channel("billpay")
                .build();
        saveToOutbox(enqueuedTopic, evt.getPaymentId(), enq);

        // 3) If threshold reached, mark batch ready and emit bill.batch.ready
        if (nextLineNo >= batchThreshold && "OPEN".equals(batch.getStatus())) {
            batch.setStatus("READY_EMITTED");
            batches.save(batch);

            BillBatchReady ready = BillBatchReady.builder()
                    .eventId(UUID.randomUUID().toString())
                    .batchId(batch.getBatchId())
                    .correlationId(evt.getCorrelationId())
                    .occurredAt(OffsetDateTime.now().toString())
                    .schemaVersion("1")
                    .channel("billpay")
                    .build();

            saveToOutbox(batchReadyTopic, batch.getBatchId(), ready);
            log.info("Batch {} reached threshold {}, emitted BillBatchReady", batch.getBatchId(), batchThreshold);
            // Dev/demo: tự phát pain.002 để hoàn tất thanh toán và đánh dấu hóa đơn PAID
            central1Simulator.simulatePain002ForBatch(batch.getBatchId());
        }
    }

    private void saveToOutbox(String topic, UUID key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            com.mockbank.billpay.worker.domain.Outbox outbox = com.mockbank.billpay.worker.domain.Outbox.builder()
                    .topic(topic)
                    .key(key.toString())
                    .payloadJson(json)
                    .state("PENDING")
                    .build();
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event for topic {} key {}", topic, key, e);
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }
}