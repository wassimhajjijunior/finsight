package com.finsight.transactionservice.dto;

import com.finsight.transactionservice.entity.Transaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID userId;
    private UUID accountId;
    private Transaction.TransactionType type;
    private Transaction.TransactionCategory category;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String merchant;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;
}