package com.finsight.authservice.exception;

import com.finsight.authservice.controller.AuthController;
import com.finsight.authservice.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validationFailure_returnsStructured400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"firstName\":\"\",\"lastName\":\"\"," +
                                "\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.message").isNotEmpty())
                .andExpect(jsonPath("$.detail.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail.field").isNotEmpty());
    }

    @Test
    void authDomainError_returnsStructuredStatus() throws Exception {
        when(authService.register(any()))
                .thenThrow(AuthException.emailAlreadyExists());

        mockMvc.perform(post("/auth/register")
                        .contentType("application/json")
                        .content("{\"firstName\":\"Wassim\",\"lastName\":\"Hajji\"," +
                                "\"email\":\"wassim@finsight.dev\"," +
                                "\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail.message")
                        .value("An account with this email already exists"))
                .andExpect(jsonPath("$.detail.code")
                        .value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void invalidCredentials_returnsStructured401() throws Exception {
        when(authService.login(any()))
                .thenThrow(AuthException.invalidCredentials());

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"wassim@finsight.dev\"," +
                                "\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail.message")
                        .value("Invalid email or password"))
                .andExpect(jsonPath("$.detail.code")
                        .value("INVALID_CREDENTIALS"));
    }

    @Test
    void unexpectedError_doesNotLeakInternals() throws Exception {
        when(authService.login(any()))
                .thenThrow(new IllegalStateException(
                        "secret internal detail: jwt secret"));

        mockMvc.perform(post("/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"wassim@finsight.dev\"," +
                                "\"password\":\"password123\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail.message")
                        .value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail.message")
                        .value(not(containsString("secret"))));
    }
}