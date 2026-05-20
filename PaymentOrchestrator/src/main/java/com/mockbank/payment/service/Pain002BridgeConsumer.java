package com.mockbank.payment.service;

import com.mockbank.commons.dto.billpay.Pain002FileMessage;
import com.mockbank.commons.dto.events.billpay.BillpayStatusEvent;
import com.mockbank.commons.dto.events.billpay.Pain002Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dev/demo bridge: consumes mock pain.002 from BillPayWorker and drives payment settlement
 * when SettlementService is not running.
 */
@Component
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
