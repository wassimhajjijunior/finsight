package com.finsight.transactionservice.service;

import com.finsight.transactionservice.entity.OutboxEvent;
import com.finsight.transactionservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Polls for unpublished outbox events every 5 seconds.
     *
     * Fixed delay means: wait 5 seconds AFTER the previous
     * execution finishes before starting the next one.
     * This prevents overlapping executions if one run is slow.
     *
     * Senior note: in production you would use Debezium CDC
     * (Change Data Capture) which reads the PostgreSQL WAL directly
     * and publishes events with sub-second latency and no polling.
     * The scheduler approach is simpler to understand and set up —
     * perfectly valid for this project.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayEvents() {

        // Process max 100 events per run
        List<OutboxEvent> unpublished = outboxEventRepository
                .findUnpublishedEvents(PageRequest.of(0, 100));

        if (unpublished.isEmpty()) return;

        log.debug("Relay: processing {} outbox events",
                unpublished.size());

        List<UUID> successfulIds = unpublished.stream()
                .filter(event -> publishToKafka(event))
                .map(OutboxEvent::getId)
                .collect(Collectors.toList());

        if (!successfulIds.isEmpty()) {
            outboxEventRepository.markAsPublished(
                    successfulIds, LocalDateTime.now());
            log.info("Relay: published {} events to Kafka",
                    successfulIds.size());
        }
    }

    private boolean publishToKafka(OutboxEvent event) {
        try {
            // Use aggregateId as the Kafka message key
            // This guarantees ordering for events from the same aggregate
            kafkaTemplate.send(
                    event.getEventType(),  // topic name
                    event.getAggregateId(), // message key
                    event.getPayload()      // JSON string
            ).get(); // .get() makes it synchronous — we need to know if it failed

            return true;
        } catch (Exception e) {
            log.error("Relay: failed to publish event id={} type={}",
                    event.getId(), event.getEventType(), e);
            return false;
            // Event stays unpublished — relay will retry on next poll
        }
    }
}