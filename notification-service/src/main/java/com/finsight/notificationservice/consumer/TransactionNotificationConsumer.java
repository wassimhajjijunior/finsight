package com.finsight.notificationservice.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.notificationservice.dto.EmailNotification;
import com.finsight.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionNotificationConsumer {

    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Sends a transaction confirmation email whenever a new
     * transaction is created. This is a lightweight notification
     * — the user sees every transaction immediately.
     *
     * In production you would add a user preference check here:
     * does this user want transaction notifications? What threshold?
     * For now we notify on every transaction.
     */
    @KafkaListener(
            topics = "transaction.created",
            groupId = "notification-service-group"
    )
    @Transactional
    public void handleTransactionCreated(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey) {

        log.debug("Received transaction.created for notification: {}",
                eventKey);

        Boolean alreadyProcessed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM processed_events " +
                        "WHERE event_id = ?",
                Boolean.class, eventKey
        );

        if (Boolean.TRUE.equals(alreadyProcessed)) {
            log.debug("Duplicate transaction notification skipped: {}",
                    eventKey);
            return;
        }

        try {
            Map<String, Object> event = objectMapper.readValue(
                    payload, new TypeReference<>() {});

            String userId = (String) event.get("userId");

            Map<String, Object> variables = new HashMap<>();
            variables.put("amount", event.get("amount"));
            variables.put("currency",
                    event.getOrDefault("currency", "USD"));
            variables.put("category",
                    event.getOrDefault("category", "OTHER"));
            variables.put("merchant", event.get("merchant"));
            variables.put("transactionDate",
                    event.get("transactionDate"));
            variables.put("type", event.get("type"));

            String userEmail = "user-" + userId + "@finsight.dev";

            EmailNotification notification = EmailNotification.builder()
                    .to(userEmail)
                    .subject("Transaction recorded — " +
                            event.getOrDefault("currency", "USD") +
                            " " + event.get("amount"))
                    .templateName("transaction-created")
                    .variables(variables)
                    .eventId(eventKey)
                    .eventType("transaction.created")
                    .build();

            emailService.sendEmail(notification);

            jdbcTemplate.update(
                    "INSERT INTO processed_events " +
                            "(event_id, event_type) VALUES (?, ?)",
                    eventKey, "transaction.created"
            );

        } catch (Exception e) {
            log.error("Error processing transaction notification: {}",
                    eventKey, e);
            throw new RuntimeException(
                    "Failed to process transaction notification", e);
        }
    }
}