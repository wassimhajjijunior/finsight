package com.finsight.accountservice.dto;

import com.finsight.accountservice.entity.Account;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AccountResponse {

    private UUID id;
    private UUID userId;
    private String name;
    private Account.AccountType type;
    private Account.AccountStatus status;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime createdAt;
}