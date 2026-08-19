package com.finsight.budgetservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@FeignClient(
        name = "transaction-service",
        fallback = TransactionClientFallback.class
)
public interface TransactionClient {

    // Get total spending for a category in a date range
    // Used by budget-service to calculate how much has been spent
    // against a budget
    @GetMapping("/transactions/spending-summary")
    BigDecimal getCategorySpending(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam UUID accountId,
            @RequestParam String category,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate
    );
}