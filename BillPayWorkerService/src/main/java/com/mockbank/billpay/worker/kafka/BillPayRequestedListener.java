package com.mockbank.billpay.worker.kafka;

import com.mockbank.commons.dto.events.billpay.*;
import com.mockbank.billpay.worker.service.BillPayWorkerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BillPayRequestedListener {

    private final BillPayWorkerService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(
            topics = "${payments.topics.billpay-requested:billpay.requested}",
            groupId = "${spring.kafka.consumer.group-id:billpay-worker-v1}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String key = record.key();
        String value = record.value();
        log.info("Consumed billpay.requested key={} value={}", key, value);

        try {
            BillPayRequested evt = objectMapper.readValue(value, BillPayRequested.class);
            service.handleRequested(evt);
        } catch (Exception e) {
            log.error("Failed to handle BillPayRequested message", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Dead Letter Topic handler: ghi log để team ops review thủ công.
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, String> record,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("DLT [BillPayRequestedListener]: message không xử lý được sau tất cả lần retry. topic={} key={} payload={}",
            topic, record.key(), record.value());
    }
}