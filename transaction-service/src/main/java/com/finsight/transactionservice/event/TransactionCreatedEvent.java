package com.finsight.transactionservice.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCreatedEvent {

    private String eventId;
    private String eventType;
    private String transactionId;
    private String userId;
    private String accountId;
    private String type;
    private String category;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String merchant;
    private LocalDateTime transactionDate;
    private LocalDateTime occurredAt;
}