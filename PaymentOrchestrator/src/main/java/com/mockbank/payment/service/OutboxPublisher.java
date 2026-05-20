package com.mockbank.payment.service;

import com.mockbank.payment.domain.Outbox;
import com.mockbank.payment.repo.OutboxRepo;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

  private static final long SEND_TIMEOUT_SECONDS = 10;

  private final OutboxRepo outboxRepo;
  private final KafkaTemplate<String, String> kafka;

  @PostConstruct
  void recoverStaleSendingRows() {
    int reset = outboxRepo.resetStaleSendingToPending();
    if (reset > 0) {
      log.warn("Reset {} outbox rows from SENDING to PENDING on startup", reset);
    }
  }

  @Scheduled(fixedDelayString = "${outbox.publish.fixedDelayMs:5000}")
  public void publish() {
    List<Outbox> batch = outboxRepo.findTop200ByStateOrderByIdAsc("PENDING");

    for (Outbox row : batch) {
      if (!markSending(row.getId())) {
        continue;
      }
      publishRow(row);
    }
  }

  private void publishRow(Outbox row) {
    final Long rowId = row.getId();
    final String topic = row.getTopic();
    final String key = row.getKey().toString();
    try {
      kafka.send(topic, key, row.getPayloadJson()).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      outboxRepo.updateState(rowId, "PUBLISHED");
      log.debug("Outbox published id={} topic={} key={}", rowId, topic, key);
    } catch (Exception ex) {
      outboxRepo.updateState(rowId, "PENDING");
      log.error("Outbox publish failed id={} topic={} key={}", rowId, topic, key, ex);
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
