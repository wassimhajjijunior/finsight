package com.finsight.budgetservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class TransactionClientFallback implements TransactionClient {

    /**
     * Fallback when transaction-service is unavailable.
     *
     * Senior decision: return BigDecimal.ZERO instead of throwing.
     * This means budget display shows 0 spent when transaction-service
     * is down — not ideal but better than the whole budget feature
     * being broken. We log the fallback so we know it happened.
     *
     * The alternative — throwing an exception — would make every
     * budget page fail when transaction-service has a blip.
     * Degraded experience beats total failure.
     */
    @Override
    public BigDecimal getCategorySpending(
            String userId,
            UUID accountId,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        log.warn("FALLBACK: transaction-service unavailable. " +
                        "Returning 0 spending for userId={} category={}",
                userId, category);

        return BigDecimal.ZERO;
    }
}