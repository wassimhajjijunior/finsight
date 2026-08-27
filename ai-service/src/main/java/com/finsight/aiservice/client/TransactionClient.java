package com.finsight.aiservice.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "transaction-service")
public interface TransactionClient {

    @GetMapping("/transactions")
    List<TransactionSummary> getRecentTransactions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    );

    @GetMapping("/transactions/spending-summary")
    BigDecimal getCategorySpending(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(name = "accountId") UUID accountId,
            @RequestParam(name = "category") String category,
            @RequestParam(name = "startDate") String startDate,
            @RequestParam(name = "endDate") String endDate
    );

    @Data
    class TransactionSummary {
        private UUID id;
        private String type;
        private String category;
        private BigDecimal amount;
        private String currency;
        private String description;
        private String merchant;
        private String transactionDate;
    }
}