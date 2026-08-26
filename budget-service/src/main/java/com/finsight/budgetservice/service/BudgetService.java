package com.finsight.budgetservice.service;

import com.finsight.budgetservice.client.TransactionClient;
import com.finsight.budgetservice.dto.BudgetResponse;
import com.finsight.budgetservice.dto.CreateBudgetRequest;
import com.finsight.budgetservice.entity.Budget;
import com.finsight.budgetservice.entity.OutboxEvent;
import com.finsight.budgetservice.repository.BudgetRepository;
import com.finsight.budgetservice.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final TransactionClient transactionClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public BudgetResponse createBudget(UUID userId,
                                       CreateBudgetRequest request) {

        Budget budget = Budget.builder()
                .userId(userId)
                .accountId(request.getAccountId())
                .name(request.getName())
                .category(request.getCategory())
                .amountLimit(request.getAmountLimit())
                .spentAmount(BigDecimal.ZERO)
                .currency(request.getCurrency().toUpperCase())
                .period(request.getPeriod())
                .periodStart(request.getPeriodStart())
                .periodEnd(request.getPeriodEnd())
                .alertThreshold(request.getAlertThreshold())
                .alertSent(false)
                .build();

        budget = budgetRepository.save(budget);
        log.info("Budget created: budgetId={} userId={} category={}",
                budget.getId(), userId, budget.getCategory());

        return toResponse(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getUserBudgets(UUID userId) {
        return budgetRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudget(UUID budgetId, UUID userId) {
        return budgetRepository
                .findByIdAndUserId(budgetId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Budget not found"));
    }

    /**
     * Refresh budget spending from transaction-service.
     *
     * This is protected by Resilience4j circuit breaker.
     * If transaction-service is down, the fallback returns BigDecimal.ZERO
     * — budget shows 0 spent, not an error.
     *
     * Called on demand when user views their budget,
     * or on a schedule to keep budgets current.
     */
    @Transactional
    public BudgetResponse refreshBudgetSpending(
            UUID budgetId, UUID userId) {

        Budget budget = budgetRepository
                .findByIdAndUserId(budgetId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Budget not found"));

        // Feign call — protected by circuit breaker via fallback
        BigDecimal spent = transactionClient.getCategorySpending(
                userId.toString(),
                budget.getAccountId(),
                budget.getCategory().name(),
                budget.getPeriodStart().atStartOfDay(),
                budget.getPeriodEnd().atTime(23, 59, 59)
        );

        boolean thresholdWasExceeded = budget.isThresholdExceeded();
        budget.setSpentAmount(spent);

        // Check if threshold is now exceeded and alert not yet sent
        if (budget.isThresholdExceeded() && !thresholdWasExceeded) {
            publishThresholdAlert(budget);
            budget.setAlertSent(true);
        }

        budget = budgetRepository.save(budget);
        log.info("Budget refreshed: budgetId={} spent={} limit={}",
                budgetId, spent, budget.getAmountLimit());

        return toResponse(budget);
    }

    /**
     * Publish budget.threshold.exceeded event to Kafka.
     * Notification-service consumes this and sends an alert.
     */
    private void publishThresholdAlert(Budget budget) {
        try {
            Map<String, Object> eventData = Map.ofEntries(
                    Map.entry("evenId", UUID.randomUUID().toString()),
                    Map.entry("eventType", "budget.threshold.exceeded"),
                    Map.entry("budgetId", budget.getId().toString()),
                    Map.entry("userId", budget.getUserId().toString()),
                    Map.entry("budgetName", budget.getName()),
                    Map.entry("category", budget.getCategory().name()),
                    Map.entry("amountLimit", budget.getAmountLimit()),
                    Map.entry("spentAmount", budget.getSpentAmount()),
                    Map.entry("spentPercentage", budget.getSpentPercentage()),
                    Map.entry("alertThreshold", budget.getAlertThreshold()),
                    Map.entry("currency", budget.getCurrency()),
                    Map.entry("occurredAt", LocalDateTime.now().toString())
            );

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(budget.getId().toString())
                    .aggregateType("Budget")
                    .eventType("budget.threshold.exceeded")
                    .payload(objectMapper.writeValueAsString(eventData))
                    .published(false)
                    .build();

            outboxEventRepository.save(outboxEvent);

            log.info("Outbox event saved: budget.threshold.exceeded " +
                    "budgetId={}", budget.getId());

        }catch (Exception e) {
            throw new RuntimeException(
                    "Failed to write budget alert to outbox", e);
        }

    }

    private BudgetResponse toResponse(Budget b) {
        BigDecimal remaining = b.getAmountLimit()
                .subtract(b.getSpentAmount());

        return BudgetResponse.builder()
                .id(b.getId())
                .userId(b.getUserId())
                .accountId(b.getAccountId())
                .name(b.getName())
                .category(b.getCategory())
                .amountLimit(b.getAmountLimit())
                .spentAmount(b.getSpentAmount())
                .remainingAmount(remaining)
                .spentPercentage(b.getSpentPercentage())
                .currency(b.getCurrency())
                .period(b.getPeriod())
                .periodStart(b.getPeriodStart())
                .periodEnd(b.getPeriodEnd())
                .alertThreshold(b.getAlertThreshold())
                .alertSent(b.isAlertSent())
                .createdAt(b.getCreatedAt())
                .build();
    }
}