package com.finsight.transactionservice.exception;

import com.finsight.transactionservice.controller.TransactionController;
import com.finsight.transactionservice.service.TransactionService;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused contract tests for the structured error response:
 * {"detail": {"message": "...", "code": "...", "field": "..."}}
 *
 * Standalone MockMvc — no Spring context, no external services.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationFailure_returnsStructured400() throws Exception {
        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"accountId\":null,\"type\":\"EXPENSE\"," +
                                "\"category\":\"FOOD\",\"amount\":0," +
                                "\"currency\":\"US\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.message").isNotEmpty())
                .andExpect(jsonPath("$.detail.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail.field").isNotEmpty());
    }

    @Test
    void resourceNotFound_returnsStructured404() throws Exception {
        when(transactionService.getTransaction(any(), any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        mockMvc.perform(get("/transactions/{txId}", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail.message").value("Transaction not found"))
                .andExpect(jsonPath("$.detail.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void feignNotFound_returnsStructured404() throws Exception {
        FeignException.NotFound feignNotFound = mock(FeignException.NotFound.class);
        when(feignNotFound.status()).thenReturn(404);
        when(transactionService.createTransaction(any(), any()))
                .thenThrow(feignNotFound);

        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"accountId\":\"" + UUID.randomUUID() +
                                "\",\"type\":\"EXPENSE\",\"category\":\"FOOD\"," +
                                "\"amount\":10.5,\"currency\":\"USD\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail.message").isNotEmpty())
                .andExpect(jsonPath("$.detail.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void feignServiceUnavailable_returnsStructured503() throws Exception {
        FeignException feignError = mock(FeignException.class);
        when(feignError.status()).thenReturn(503);
        when(transactionService.createTransaction(any(), any()))
                .thenThrow(feignError);

        mockMvc.perform(post("/transactions")
                        .header("X-User-Id", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"accountId\":\"" + UUID.randomUUID() +
                                "\",\"type\":\"EXPENSE\",\"category\":\"FOOD\"," +
                                "\"amount\":10.5,\"currency\":\"USD\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail.message")
                        .value("Upstream service unavailable"))
                .andExpect(jsonPath("$.detail.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void unexpectedError_doesNotLeakInternals() throws Exception {
        when(transactionService.getTransaction(any(), any()))
                .thenThrow(new IllegalStateException(
                        "secret internal detail: db password"));

        mockMvc.perform(get("/transactions/{txId}", UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail.message")
                        .value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail.message")
                        .value(not(containsString("secret"))));
    }
}