package com.finsight.budgetservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "budgets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "budget_category")
    private BudgetCategory category;

    @Column(name = "amount_limit", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal amountLimit;

    @Column(name = "spent_amount", nullable = false,
            precision = 19, scale = 4)
    private BigDecimal spentAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "budget_period")
    private BudgetPeriod period;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    // Percentage (0-100) at which to fire the alert
    @Column(name = "alert_threshold", nullable = false)
    private Integer alertThreshold;

    // Prevents duplicate alerts for the same threshold breach
    @Column(name = "alert_sent", nullable = false)
    private boolean alertSent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Calculated field — not stored in DB
    // Derived from spentAmount / amountLimit * 100
    public double getSpentPercentage() {
        if (amountLimit.compareTo(BigDecimal.ZERO) == 0) return 0;
        return spentAmount
                .divide(amountLimit, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    public boolean isThresholdExceeded() {
        return getSpentPercentage() >= alertThreshold && !alertSent;
    }

    public enum BudgetCategory {
        FOOD, TRANSPORT, HOUSING, HEALTHCARE,
        ENTERTAINMENT, SHOPPING, SALARY, INVESTMENT,
        TRANSFER, OTHER
    }

    public enum BudgetPeriod {
        WEEKLY, MONTHLY, YEARLY
    }
}