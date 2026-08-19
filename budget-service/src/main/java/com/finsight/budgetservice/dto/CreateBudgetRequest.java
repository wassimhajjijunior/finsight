package com.finsight.budgetservice.dto;

import com.finsight.budgetservice.entity.Budget;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateBudgetRequest {

    @NotNull
    private UUID accountId;

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    private Budget.BudgetCategory category;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amountLimit;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    @NotNull
    private Budget.BudgetPeriod period;

    @NotNull
    private LocalDate periodStart;

    @NotNull
    private LocalDate periodEnd;

    @Min(1) @Max(100)
    private Integer alertThreshold = 80;
}