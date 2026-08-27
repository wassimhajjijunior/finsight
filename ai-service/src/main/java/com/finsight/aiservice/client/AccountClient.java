package com.finsight.aiservice.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "account-service")
public interface AccountClient {

    @GetMapping("/accounts")
    List<AccountSummary> getUserAccounts(
            @RequestHeader("X-User-Id") String userId
    );

    @Data
    class AccountSummary {
        private UUID id;
        private String name;
        private String type;
        private BigDecimal balance;
        private String currency;
        private String status;
    }
}