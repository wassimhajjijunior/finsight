package com.finsight.aiservice.service;

import com.finsight.aiservice.client.AccountClient;
import com.finsight.aiservice.client.TransactionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ToolService {

    private final AccountClient accountClient;
    private final TransactionClient transactionClient;

    // Thread-local to hold userId during a request
    // Tool methods are called by the LLM — they need the userId
    // but Spring AI does not pass request context automatically
    private static final ThreadLocal<String> CURRENT_USER =
            new ThreadLocal<>();

    public static void setCurrentUser(String userId) {
        CURRENT_USER.set(userId);
    }

    public static void clearCurrentUser() {
        CURRENT_USER.remove();
    }

    @Tool(description = """
        Get the user's financial accounts with current balances.
        Use this when the user asks about their accounts, balances,
        or total wealth overview.
        """)
    public String getAccountBalances() {
        String userId = CURRENT_USER.get();
        log.info("Tool called: getAccountBalances userId={}", userId);

        List<AccountClient.AccountSummary> accounts =
                accountClient.getUserAccounts(userId);

        if (accounts.isEmpty()) {
            return "The user has no accounts registered.";
        }

        return accounts.stream()
                .map(a -> String.format(
                        "Account: %s (%s) — Balance: %s %s",
                        a.getName(), a.getType(),
                        a.getBalance(), a.getCurrency()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(description = """
        Get the user's most recent transactions.
        Use this when the user asks about recent spending,
        what they bought recently, or transaction history.
        Returns the 10 most recent transactions.
        """)
    public String getRecentTransactions() {
        String userId = CURRENT_USER.get();
        log.info("Tool called: getRecentTransactions userId={}", userId);

        List<TransactionClient.TransactionSummary> transactions =
                transactionClient.getRecentTransactions(userId, 0, 10)
                        .stream()
                        .collect(Collectors.toList());

        if (transactions.isEmpty()) {
            return "No recent transactions found.";
        }

        return transactions.stream()
                .map(t -> String.format(
                        "[%s] %s — %s %s at %s on %s",
                        t.getType(), t.getCategory(),
                        t.getAmount(), t.getCurrency(),
                        t.getMerchant() != null ? t.getMerchant() : "unknown",
                        t.getTransactionDate()))
                .collect(Collectors.joining("\n"));
    }
}