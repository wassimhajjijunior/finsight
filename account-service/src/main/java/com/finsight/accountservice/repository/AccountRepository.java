package com.finsight.accountservice.repository;

import com.finsight.accountservice.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // Uses idx_accounts_user_id index
    List<Account> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Uses idx_accounts_user_status composite index
    List<Account> findByUserIdAndStatus(
            UUID userId, Account.AccountStatus status);

    // Verify ownership before any mutation
    // Critical security check — user can only modify their own accounts
    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    // Used by transaction-service via Feign to verify account exists
    // and belongs to the requesting user
    @Query("SELECT a FROM Account a WHERE a.id = :id " +
            "AND a.userId = :userId " +
            "AND a.status = 'ACTIVE'")
    Optional<Account> findActiveAccountByIdAndUserId(
            UUID id, UUID userId);
}