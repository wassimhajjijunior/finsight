package com.finsight.aiservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.aiservice.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "transaction.created",
            groupId = "ai-service-group"
    )
    @Transactional
    public void handleTransactionCreated(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey) {

        log.debug("Received transaction.created event key={}", eventKey);

        // Idempotency check
        Boolean alreadyProcessed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM processed_events " +
                        "WHERE event_id = ?",
                Boolean.class, eventKey
        );

        if (Boolean.TRUE.equals(alreadyProcessed)) {
            log.debug("Duplicate event skipped: {}", eventKey);
            return;
        }

        try {
            // Parse the JSON event payload
            Map<String, Object> event = objectMapper.readValue(
                    payload,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {}
            );

            // Index the transaction for RAG
            embeddingService.indexTransaction(event);

            // Mark as processed in same transaction
            jdbcTemplate.update(
                    "INSERT INTO processed_events(event_id) VALUES (?)",
                    eventKey
            );

            log.info("Transaction indexed for RAG: eventKey={}", eventKey);

        } catch (Exception e) {
            log.error("Failed to index transaction event: {}",
                    eventKey, e);
            // Let the exception propagate — Kafka will retry
            throw new RuntimeException(
                    "Failed to process transaction event", e);
        }
    }
}