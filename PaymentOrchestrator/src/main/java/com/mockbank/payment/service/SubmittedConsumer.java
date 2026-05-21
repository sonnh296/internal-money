package com.mockbank.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.ProcessedEvent;
import com.mockbank.commons.dto.events.billpay.*;
import com.mockbank.payment.repo.PaymentRepo;
import com.mockbank.payment.repo.ProcessedEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubmittedConsumer {
  private final PaymentRepo paymentRepo;
  private final ProcessedEventRepo processed;
  private final ObjectMapper om;

  @RetryableTopic(
      attempts = "4",
      backoff = @Backoff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR)
  @KafkaListener(topics = "${payments.topics.bill-batch-submitted:bill.batch.submitted}", groupId = "payment-api")
  @Transactional
  public void onMessage(String message) throws Exception {
    var evt = om.readValue(message, BillBatchSubmitted.class);

    String eventId = evt.getEventId();
    if (eventId == null || eventId.isBlank()) {
      eventId = evt.getBatchId();
    }

    if (processed.existsByHandlerAndEventId("submitted", eventId)) {
      return;
    }

    List<Payment> list = paymentRepo.findAllByBatchId(UUID.fromString(evt.getBatchId()));
    var now = OffsetDateTime.now();
    for (var p : list) {
      if (PaymentStateTransitions.applySubmitted(p)) {
        p.setUpdatedAt(now);
      } else {
        log.info("Bỏ qua submitted — không hạ state paymentId={} current={}",
            p.getPaymentId(), p.getState());
      }
    }
    paymentRepo.saveAll(list);

    processed.save(ProcessedEvent.builder()
        .handler("submitted")
        .eventId(eventId)
        .processedAt(now)
        .build());
  }

  @DltHandler
  public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    log.error("DLT [SubmittedConsumer]: message không xử lý được sau tất cả lần retry. topic={} payload={}",
        topic, message);
  }
}
