package com.finsight.transactionservice.dto;

import com.finsight.transactionservice.entity.Transaction;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateTransactionRequest {

    @NotNull(message = "Account ID is required")
    private UUID accountId;

    @NotNull(message = "Transaction type is required")
    private Transaction.TransactionType type;

    @NotNull(message = "Category is required")
    private Transaction.TransactionCategory category;

    @NotNull
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    @Size(max = 500)
    private String description;

    @Size(max = 200)
    private String merchant;

    private LocalDateTime transactionDate;
}