package com.finsight.accountservice.dto;

import com.finsight.accountservice.entity.Account;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAccountRequest {

    @NotBlank(message = "Account name is required")
    @Size(max = 100)
    private String name;

    @NotNull(message = "Account type is required")
    private Account.AccountType type;

    @NotBlank
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;
}