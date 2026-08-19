package com.finsight.budgetservice.repository;

import com.finsight.budgetservice.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    // Find active budgets for a user in a specific category
    // Used when a transaction comes in to check which budgets are affected
    @Query("SELECT b FROM Budget b WHERE b.userId = :userId " +
            "AND b.category = :category " +
            "AND b.periodStart <= :date " +
            "AND b.periodEnd >= :date")
    List<Budget> findActiveBudgetsForCategory(
            UUID userId,
            Budget.BudgetCategory category,
            LocalDate date
    );

    // Find budgets that have exceeded threshold
    // and alert has not been sent yet
    @Query("SELECT b FROM Budget b WHERE b.userId = :userId " +
            "AND b.alertSent = false " +
            "AND (b.spentAmount / b.amountLimit * 100) >= b.alertThreshold")
    List<Budget> findBudgetsExceedingThreshold(UUID userId);

    // Atomic update of spent amount
    // Using @Modifying + @Query avoids loading the entity
    // just to update one field — more efficient
    @Modifying
    @Query("UPDATE Budget b SET b.spentAmount = :spentAmount, " +
            "b.alertSent = :alertSent " +
            "WHERE b.id = :budgetId")
    void updateSpentAmount(UUID budgetId,
                           java.math.BigDecimal spentAmount,
                           boolean alertSent);
}