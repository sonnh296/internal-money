package com.settlement.service;

import com.settlement.domain.Outbox;
import com.settlement.repo.OutboxRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxRepo outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.publish.fixedDelayMs:5000}")
    @Transactional
    public void publishPendingMessages() {
        List<Outbox> pending = outboxRepo.findPendingWithLock();
        if (pending.isEmpty()) return;

        for (Outbox msg : pending) {
            try {
                kafkaTemplate.send(msg.getTopic(), msg.getKey(), msg.getPayloadJson());
                msg.setState("PUBLISHED");
                msg.setUpdatedAt(OffsetDateTime.now());
                outboxRepo.save(msg);
            } catch (Exception e) {
                log.error("Failed to publish outbox id={}", msg.getId(), e);
            }
        }
    }
}
