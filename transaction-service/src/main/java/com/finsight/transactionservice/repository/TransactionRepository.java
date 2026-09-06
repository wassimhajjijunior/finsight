package com.finsight.transactionservice.repository;

import com.finsight.transactionservice.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, UUID> {

    // Paginated — never return all transactions without pagination
    // A user could have 10,000 transactions
    Page<Transaction> findByUserIdOrderByTransactionDateDesc(
            UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndAccountIdOrderByTransactionDateDesc(
            UUID userId, UUID accountId, Pageable pageable);

    // Ownership verification before any read
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    // Aggregate for budget analysis — total spend by category in date range
    @Query("SELECT t.category, SUM(t.amount) FROM Transaction t " +
            "WHERE t.userId = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.transactionDate BETWEEN :start AND :end " +
            "GROUP BY t.category")
    List<Object[]> sumExpensesByCategory(
            @Param("userId") UUID userId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Total spending in a date range — used by budget-service via Feign
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.userId = :userId " +
            "AND t.accountId = :accountId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.transactionDate BETWEEN :start AND :end")
    BigDecimal sumExpensesByAccountAndDateRange(
            @Param("userId") UUID userId, @Param("accountId") UUID accountId,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}