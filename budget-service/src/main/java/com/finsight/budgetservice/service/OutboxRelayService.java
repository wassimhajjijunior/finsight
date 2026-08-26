package com.finsight.budgetservice.service;

import com.finsight.budgetservice.entity.OutboxEvent;
import com.finsight.budgetservice.repository.OutboxEventRepository;
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

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void relayEvents() {

        List<OutboxEvent> unpublished = outboxEventRepository
                .findUnpublishedEvents(PageRequest.of(0, 100));

        if (unpublished.isEmpty()) return;

        List<UUID> successfulIds = unpublished.stream()
                .filter(this::publishToKafka)
                .map(OutboxEvent::getId)
                .collect(Collectors.toList());

        if (!successfulIds.isEmpty()) {
            outboxEventRepository.markAsPublished(
                    successfulIds, LocalDateTime.now());
            log.info("Budget relay: published {} events",
                    successfulIds.size());
        }
    }

    private boolean publishToKafka(OutboxEvent event) {
        try {
            kafkaTemplate.send(
                    event.getEventType(),
                    event.getAggregateId(),
                    event.getPayload()
            ).get();
            return true;
        } catch (Exception e) {
            log.error("Budget relay failed for event id={}",
                    event.getId(), e);
            return false;
        }
    }
}