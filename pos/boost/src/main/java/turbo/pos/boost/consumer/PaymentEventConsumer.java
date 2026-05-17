package turbo.pos.boost.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import turbo.pos.boost.dto.TransactionRequest;
import turbo.pos.boost.service.LockingRewardService;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consumes payment.completed events from PaymentOrchestrator.
 * For each completed payment, issues reward points to the customer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final LockingRewardService rewardService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.completed", groupId = "pos-boost-rewards")
    public void onPaymentCompleted(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);
            String customerId = (String) event.get("customerId");
            String transactionId = (String) event.get("transactionId");
            BigDecimal amount = new BigDecimal(String.valueOf(event.getOrDefault("amount", "0")));

            if (customerId == null || transactionId == null) {
                log.warn("Received payment.completed event with missing fields: {}", message);
                return;
            }

            Object schemaVersion = event.get("schemaVersion");
            if (schemaVersion != null && !String.valueOf(schemaVersion).equals("1")) {
                log.warn("Unsupported schema version: {}", schemaVersion);
                return;
            }

            TransactionRequest req = new TransactionRequest(customerId, transactionId, amount);
            rewardService.processReward(req);
            log.info("Processed reward for customerId={}, transactionId={}, amount={}", customerId, transactionId, amount);
        } catch (Exception e) {
            log.error("Failed to process payment.completed event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process reward event", e);
        }
    }

    @org.springframework.kafka.annotation.DltHandler
    public void handleDlt(String message, @org.springframework.kafka.support.KafkaHeaders org.springframework.messaging.handler.annotation.Header(org.springframework.kafka.support.KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("Dead letter from topic {}: {}", topic, message);
        // store to DB or notify alerts
    }
}
