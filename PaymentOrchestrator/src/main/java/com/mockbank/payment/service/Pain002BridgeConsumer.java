package com.mockbank.payment.service;

import com.mockbank.commons.dto.billpay.Pain002FileMessage;
import com.mockbank.commons.dto.events.billpay.BillpayStatusEvent;
import com.mockbank.commons.dto.events.billpay.Pain002Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dev/demo bridge: consumes mock pain.002 khi SettlementService không chạy.
 * Tắt mặc định — bật bằng payments.pain002-bridge.enabled=true (không chạy song song Settlement).
 */
@Component
@ConditionalOnProperty(name = "payments.pain002-bridge.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class Pain002BridgeConsumer {

  private final ObjectMapper objectMapper;
  private final StatusConsumer statusConsumer;

  @KafkaListener(topics = "central1.pain002", groupId = "payment-pain002-bridge")
  public void onPain002File(String message) throws Exception {
    Pain002FileMessage file = objectMapper.readValue(message, Pain002FileMessage.class);
    log.info("Bridge received pain.002 batchId={} items={}", file.batchId(), file.items().size());
    for (Pain002Message item : file.items()) {
      String status = item.success() ? "POSTED" : "FAILED";
      BillpayStatusEvent evt = new BillpayStatusEvent(
          UUID.randomUUID(),
          item.paymentId(),
          item.batchId(),
          status,
          item.reason(),
          item.receivedAt()
      );
      statusConsumer.onMessage(objectMapper.writeValueAsString(evt));
    }
  }
}
