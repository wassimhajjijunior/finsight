package com.finsight.aiservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.aiservice.service.EmbeddingService;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;
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
    private final Tracer tracer;

    @KafkaListener(
            topics = "transaction.created",
            groupId = "ai-service-group"
    )
    @Transactional
    public void handleTransactionCreated(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_KEY) String eventKey) {

        log.debug("Received transaction.created event key={}", eventKey);

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
            Map<String, Object> event = objectMapper.readValue(
                    payload,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {}
            );

            String transactionId = (String) event.get("transactionId");
            String userId = (String) event.get("userId");

            Span span = tracer.nextSpan()
                    .name("index-transaction-for-rag")
                    .tag("transactionId", transactionId != null ? transactionId : "unknown")
                    .tag("userId", userId != null ? userId : "unknown")
                    .tag("eventKey", eventKey)
                    .start();

            try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
                embeddingService.indexTransaction(event);
            } finally {
                span.end();
            }

            jdbcTemplate.update(
                    "INSERT INTO processed_events(event_id) VALUES (?)",
                    eventKey
            );

            log.info("Transaction indexed for RAG: eventKey={}", eventKey);

        } catch (Exception e) {
            log.error("Failed to index transaction event: {}",
                    eventKey, e);
            throw new RuntimeException(
                    "Failed to process transaction event", e);
        }
    }
}