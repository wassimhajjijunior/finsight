package com.finsight.transactionservice.controller;

import com.finsight.transactionservice.dto.CreateTransactionRequest;
import com.finsight.transactionservice.dto.TransactionResponse;
import com.finsight.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateTransactionRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(userId, request));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> getUserTransactions(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 20, sort = "transactionDate")
            Pageable pageable) {

        return ResponseEntity.ok(
                transactionService.getUserTransactions(userId, pageable));
    }

    @GetMapping("/{txId}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable("txId") UUID txId,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(
                transactionService.getTransaction(txId, userId));
    }


    @GetMapping("/spending-summary")
    public ResponseEntity<BigDecimal> getSpendingSummary(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam("accountId") UUID accountId,
            @RequestParam("category") String category,
            @RequestParam("startDate") LocalDateTime startDate,
            @RequestParam("endDate") LocalDateTime endDate) {

        return ResponseEntity.ok(
                transactionService.getCategorySpending(
                        userId, accountId, category, startDate, endDate));
    }
}