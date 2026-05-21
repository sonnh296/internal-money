package com.mockbank.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockbank.payment.domain.Payment;
import com.mockbank.payment.domain.PaymentState;
import com.mockbank.payment.domain.ProcessedEvent;
import com.mockbank.commons.dto.events.EventSchemaSupport;
import com.mockbank.commons.dto.events.billpay.*;
import com.mockbank.payment.repo.PaymentRepo;
import com.mockbank.payment.repo.ProcessedEventRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
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

  @RetryableTopic(
      attempts = "4",
      backoff = @Backoff(delay = 1000, multiplier = 2.0),
      dltStrategy = DltStrategy.FAIL_ON_ERROR)
  @KafkaListener(topics = "${payments.topics.billpay-enqueued:billpay.enqueued}", groupId = "payment-api")
  @Transactional
  public void onMessage(String message) throws Exception {
    var evt = om.readValue(message, BillPayEnqueued.class);
    EventSchemaSupport.requireVersion1(evt.getSchemaVersion(), "BillPayEnqueued");
    if (processed.existsByHandlerAndEventId("enqueued", evt.getEventId())) return;

    Payment p = paymentRepo.findById(evt.getPaymentId()).orElse(null);
    if (p != null) {
      if (PaymentStateTransitions.applyBatched(p, evt.getBatchId())) {
        p.setUpdatedAt(OffsetDateTime.now());
        paymentRepo.save(p);
      } else {
        log.info("Bỏ qua enqueued — không hạ state paymentId={} current={}",
            p.getPaymentId(), p.getState());
        if (p.getBatchId() == null && evt.getBatchId() != null) {
          p.setBatchId(evt.getBatchId());
          paymentRepo.save(p);
        }
      }
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
