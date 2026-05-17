package com.payments.orch.service;

import com.payments.orch.domain.Outbox;
import com.payments.orch.repo.OutboxRepo;
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

  private final OutboxRepo outboxRepo;
  private final KafkaTemplate<String, String> kafka;

  /**
   * Đánh dấu SENDING trong TX trước khi gửi Kafka — giảm duplicate khi crash giữa send và update.
   */
  @Scheduled(fixedDelayString = "${outbox.publish.fixedDelayMs:5000}")
  public void publish() {
    List<Outbox> batch = outboxRepo.findTop200ByStateOrderByIdAsc("PENDING");

    for (Outbox row : batch) {
      if (!markSending(row.getId())) {
        continue;
      }
      final Long rowId = row.getId();
      final String topic = row.getTopic();
      final String key = row.getKey().toString();

      kafka.send(topic, key, row.getPayloadJson())
          .whenComplete((result, ex) -> {
            if (ex == null) {
              outboxRepo.updateState(rowId, "PUBLISHED");
              log.debug("Outbox published id={} topic={} key={}", rowId, topic, key);
            } else {
              outboxRepo.updateState(rowId, "PENDING");
              log.error("Outbox publish failed id={} topic={} key={}", rowId, topic, key, ex);
            }
          });
    }
  }

  @Transactional
  boolean markSending(Long id) {
    int updated = outboxRepo.markStateIfCurrent(id, "PENDING", "SENDING");
    return updated > 0;
  }

  @Transactional
  public void markFailed(Long id) {
    outboxRepo.updateState(id, "FAILED");
  }
}
