package com.finsight.transactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.transactionservice.client.AccountClient;
import com.finsight.transactionservice.dto.CreateTransactionRequest;
import com.finsight.transactionservice.dto.TransactionResponse;
import com.finsight.transactionservice.entity.OutboxEvent;
import com.finsight.transactionservice.entity.Transaction;
import com.finsight.transactionservice.event.TransactionCreatedEvent;
import com.finsight.transactionservice.repository.OutboxEventRepository;
import com.finsight.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    @Transactional
    public TransactionResponse createTransaction(
            UUID userId, CreateTransactionRequest request) {

        // Verify the account exists and belongs to this user
        // Feign call to account-service — if account not found,
        // Feign throws FeignException which we let propagate as 404
        AccountClient.AccountDto account = accountClient.getActiveAccount(
                request.getAccountId(),
                userId.toString()
        );

        log.debug("Account verified: accountId={} currency={}",
                account.getId(), account.getCurrency());

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .accountId(request.getAccountId())
                .type(request.getType())
                .category(request.getCategory() != null
                        ? request.getCategory()
                        : Transaction.TransactionCategory.OTHER)
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .description(request.getDescription())
                .merchant(request.getMerchant())
                .transactionDate(request.getTransactionDate() != null
                        ? request.getTransactionDate()
                        : LocalDateTime.now())
                .build();

        transaction = transactionRepository.save(transaction);

        log.info("Transaction created: txId={} userId={} amount={} type={}",
                transaction.getId(), userId,
                transaction.getAmount(), transaction.getType());


        try {
            TransactionCreatedEvent event = TransactionCreatedEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("transaction.created")
                    .transactionId(transaction.getId().toString())
                    .userId(userId.toString())
                    .accountId(transaction.getAccountId().toString())
                    .type(transaction.getType().name())
                    .category(transaction.getCategory().name())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .description(transaction.getDescription())
                    .merchant(transaction.getMerchant())
                    .transactionDate(transaction.getTransactionDate())
                    .occurredAt(LocalDateTime.now())
                    .build();

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(transaction.getId().toString())
                    .aggregateType("Transaction")
                    .eventType("transaction.created")
                    .payload(objectMapper.writeValueAsString(event))
                    .published(false)
                    .build();

            outboxEventRepository.save(outboxEvent);

            log.debug("Outbox event saved for transactionId={}",
                    transaction.getId());

        } catch (Exception e) {
            // If outbox write fails, the whole transaction rolls back
            // Better to fail the transaction than to save without the event
            throw new RuntimeException(
                    "Failed to write transaction event to outbox", e);
        }

        return toResponse(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getUserTransactions(
            UUID userId, Pageable pageable) {

        return transactionRepository
                .findByUserIdOrderByTransactionDateDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(UUID txId, UUID userId) {
        return transactionRepository
                .findByIdAndUserId(txId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private TransactionResponse toResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .userId(t.getUserId())
                .accountId(t.getAccountId())
                .type(t.getType())
                .category(t.getCategory())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .description(t.getDescription())
                .merchant(t.getMerchant())
                .transactionDate(t.getTransactionDate())
                .createdAt(t.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public BigDecimal getCategorySpending(
            UUID userId,
            UUID accountId,
            String category,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        Transaction.TransactionCategory cat =
                Transaction.TransactionCategory.valueOf(category);

        BigDecimal result = transactionRepository
                .sumExpensesByAccountAndDateRange(
                        userId, accountId, startDate, endDate);

        return result != null ? result : BigDecimal.ZERO;
    }
}