package com.finsight.transactionservice.service;

import com.finsight.transactionservice.client.AccountClient;
import com.finsight.transactionservice.dto.CreateTransactionRequest;
import com.finsight.transactionservice.dto.TransactionResponse;
import com.finsight.transactionservice.entity.Transaction;
import com.finsight.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

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

        // TODO Step 9: publish transaction.created event to Kafka here

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
}