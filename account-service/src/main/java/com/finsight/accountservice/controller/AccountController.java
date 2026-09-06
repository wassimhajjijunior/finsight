package com.finsight.accountservice.controller;

import com.finsight.accountservice.dto.AccountResponse;
import com.finsight.accountservice.dto.CreateAccountRequest;
import com.finsight.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // userId extracted from X-User-Id header injected by Gateway
    // Never trust a userId from the request body — always from the header
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateAccountRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(accountService.createAccount(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getUserAccounts(
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(accountService.getUserAccounts(userId));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable(name = "accountId") UUID accountId,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(
                accountService.getAccount(accountId, userId));
    }

    // Internal endpoint called by transaction-service via Feign
    // Verifies account exists and is active before a transaction is added
    @GetMapping("/{accountId}/active")
    public ResponseEntity<AccountResponse> getActiveAccount(
            @PathVariable(name = "accountId") UUID accountId,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(
                accountService.getActiveAccount(accountId, userId));
    }
}