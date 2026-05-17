package com.payments.orch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.orch.domain.Payment;
import com.payments.orch.domain.PaymentState;
import com.payments.orch.domain.ProcessedEvent;
import com.events.billpay.*;
import com.payments.orch.repo.PaymentRepo;
import com.payments.orch.repo.ProcessedEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class EnqueuedConsumer {

  private final PaymentRepo paymentRepo;
  private final ProcessedEventRepo processed;
  private final ObjectMapper om;

  @KafkaListener(topics="billpay.enqueued", groupId="payment-api")
  @Transactional
  public void onMessage(String message) throws Exception {
    var evt = om.readValue(message, BillPayEnqueued.class);
    if (processed.existsByHandlerAndEventId("enqueued", evt.getEventId())) return;

    Payment p = paymentRepo.findById(evt.getPaymentId()).orElse(null);
    if (p != null) {
      p.setState(PaymentState.BATCHED);
      p.setBatchId(evt.getBatchId());
      p.setUpdatedAt(OffsetDateTime.now());
      paymentRepo.save(p);
    }

    processed.save(ProcessedEvent.builder()
        .handler("enqueued")
        .eventId(evt.getEventId())
        .processedAt(OffsetDateTime.now())
        .build());
  }

  /**
   * Dead Letter Topic handler: log để team ops review và xử lý thủ công.
   */
  @DltHandler
  public void handleDlt(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
    log.error("DLT [EnqueuedConsumer]: message không xử lý được sau tất cả lần retry. topic={} payload={}",
        topic, message);
  }
}
