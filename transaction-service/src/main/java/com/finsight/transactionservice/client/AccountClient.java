package com.finsight.transactionservice.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.util.UUID;

// name must match exactly how account-service registered with Eureka
// Feign resolves this name via Eureka to get the actual IP:port
@FeignClient(name = "account-service")
public interface AccountClient {

    @GetMapping("/accounts/{accountId}/active")
    AccountDto getActiveAccount(
            @PathVariable UUID accountId,
            @RequestHeader("X-User-Id") String userId
    );

    // Lightweight DTO — only what transaction-service needs
    @Data
    class AccountDto {
        private UUID id;
        private UUID userId;
        private String currency;
        private BigDecimal balance;
        private String status;
    }
}