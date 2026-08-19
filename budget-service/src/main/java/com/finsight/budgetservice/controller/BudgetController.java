package com.finsight.budgetservice.controller;

import com.finsight.budgetservice.dto.BudgetResponse;
import com.finsight.budgetservice.dto.CreateBudgetRequest;
import com.finsight.budgetservice.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping
    public ResponseEntity<BudgetResponse> createBudget(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateBudgetRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(budgetService.createBudget(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<BudgetResponse>> getUserBudgets(
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(budgetService.getUserBudgets(userId));
    }

    @GetMapping("/{budgetId}")
    public ResponseEntity<BudgetResponse> getBudget(
            @PathVariable UUID budgetId,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(
                budgetService.getBudget(budgetId, userId));
    }

    // Manually trigger spending refresh for a budget
    @PostMapping("/{budgetId}/refresh")
    public ResponseEntity<BudgetResponse> refreshBudget(
            @PathVariable UUID budgetId,
            @RequestHeader("X-User-Id") UUID userId) {

        return ResponseEntity.ok(
                budgetService.refreshBudgetSpending(budgetId, userId));
    }
}