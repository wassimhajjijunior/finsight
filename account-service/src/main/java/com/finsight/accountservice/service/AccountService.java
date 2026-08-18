package com.finsight.accountservice.service;

import com.finsight.accountservice.dto.AccountResponse;
import com.finsight.accountservice.dto.CreateAccountRequest;
import com.finsight.accountservice.entity.Account;
import com.finsight.accountservice.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponse createAccount(UUID userId,
                                         CreateAccountRequest request) {
        Account account = Account.builder()
                .userId(userId)
                .name(request.getName())
                .type(request.getType())
                .status(Account.AccountStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .currency(request.getCurrency().toUpperCase())
                .build();

        account = accountRepository.save(account);
        log.info("Account created: accountId={} userId={}",
                account.getId(), userId);

        return toResponse(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(UUID userId) {
        return accountRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return accountRepository
                .findByIdAndUserId(accountId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));
    }

    // Called internally by transaction-service via Feign
    // Returns a lightweight response — balance + currency only
    @Transactional(readOnly = true)
    public AccountResponse getActiveAccount(UUID accountId, UUID userId) {
        return accountRepository
                .findActiveAccountByIdAndUserId(accountId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Active account not found"
                ));
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .name(account.getName())
                .type(account.getType())
                .status(account.getStatus())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .build();
    }
}