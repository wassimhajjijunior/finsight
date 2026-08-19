package com.finsight.budgetservice.dto;

import com.finsight.budgetservice.entity.Budget;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BudgetResponse {

    private UUID id;
    private UUID userId;
    private UUID accountId;
    private String name;
    private Budget.BudgetCategory category;
    private BigDecimal amountLimit;
    private BigDecimal spentAmount;
    private BigDecimal remainingAmount;
    private double spentPercentage;
    private String currency;
    private Budget.BudgetPeriod period;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer alertThreshold;
    private boolean alertSent;
    private LocalDateTime createdAt;
}