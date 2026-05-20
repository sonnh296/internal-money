package com.mockbank.billpay.worker.service;

import com.mockbank.billpay.worker.domain.Outbox;
import com.mockbank.billpay.worker.repo.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.publish.fixedDelayMs:5000}")
    public void publish() {
        List<Outbox> batch = outboxRepository.findTop200ByStateOrderByIdAsc("PENDING");

        for (Outbox row : batch) {
            if (!markSending(row.getId())) {
                continue;
            }
            final Long rowId = row.getId();
            final String topic = row.getTopic();
            final String key = row.getKey();

            kafkaTemplate.send(topic, key, row.getPayloadJson())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        outboxRepository.updateState(rowId, "PUBLISHED");
                        log.debug("Outbox published id={} topic={} key={}", rowId, topic, key);
                    } else {
                        outboxRepository.updateState(rowId, "PENDING");
                        log.error("Outbox publish failed id={} topic={} key={}", rowId, topic, key, ex);
                    }
                });
        }
    }

    @Transactional
    boolean markSending(Long id) {
        int updated = outboxRepository.markStateIfCurrent(id, "PENDING", "SENDING");
        return updated > 0;
    }
}
