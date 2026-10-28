package com.finsight.transactionservice.exception;

import feign.FeignException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Central exception contract for transaction-service.
 *
 * Every error leaves this service in the same shape:
 * {"detail": {"message": "...", "code": "...", "field": "..."}}
 *
 * Handlers:
 * - Validation (body, params, headers, unreadable payload) -> 400 VALIDATION_ERROR
 * - Domain/not-found errors already thrown by the service   -> mapped to their status
 * - Feign failures against account-service                  -> 404 / 503 mapped to the contract
 * - Unexpected errors                                      -> safe generic 500, no internals leaked
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {

        FieldError fieldError = ex.getBindingResult()
                .getFieldErrors().stream().findFirst().orElse(null);

        String field = fieldError != null ? fieldError.getField() : null;
        String message = fieldError != null
                ? fieldError.getDefaultMessage()
                : "Validation failed";

        log.warn("Validation error on field '{}': {}", field, message);
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", field);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {

        ConstraintViolation<?> violation = ex.getConstraintViolations()
                .stream().findFirst().orElse(null);

        String field = violation != null
                ? violation.getPropertyPath().toString() : null;
        String message = violation != null
                ? violation.getMessage() : "Validation failed";

        log.warn("Constraint violation on '{}': {}", field, message);
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", field);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        String field = ex.getName();
        String message = "Invalid value for parameter '" + field + "'";
        log.warn("Type mismatch on '{}': {}", field, ex.getValue());
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", field);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex) {

        log.warn("Malformed request body: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "Malformed request body", "VALIDATION_ERROR", null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(
            MissingRequestHeaderException ex) {

        String field = ex.getHeaderName();
        String message = "Required header '" + field + "' is missing";
        log.warn("Missing header '{}'", field);
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", field);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex) {

        String field = ex.getParameterName();
        String message = "Required parameter '" + field + "' is missing";
        log.warn("Missing parameter '{}'", field);
        return build(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR", field);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException ex) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        String message = ex.getReason() != null
                ? ex.getReason() : status.getReasonPhrase();

        log.warn("Request failed ({}): {}", status.value(), message);
        return build(status, message, codeFor(status), null);
    }

    /**
     * Feign failures against account-service.
     *
     * - Upstream 404 (account not found) -> 404 RESOURCE_NOT_FOUND
     * - Upstream 5xx / unavailable      -> 503 SERVICE_UNAVAILABLE
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiErrorResponse> handleFeign(FeignException ex) {
        int status = ex.status();

        if (status == HttpStatus.NOT_FOUND.value()) {
            log.warn("Upstream resource not found: {}", ex.getMessage());
            return build(HttpStatus.NOT_FOUND,
                    "Requested resource not found", "RESOURCE_NOT_FOUND", null);
        }

        if (status <= 0 || status >= HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            log.error("Upstream service error: status={}", status, ex);
            return build(HttpStatus.SERVICE_UNAVAILABLE,
                    "Upstream service unavailable", "SERVICE_UNAVAILABLE", null);
        }

        return build(HttpStatus.BAD_GATEWAY,
                "Upstream service error", "UPSTREAM_ERROR", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneral(Exception ex) {
        // Never leak stack traces or internal details to the client
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", "INTERNAL_ERROR", null);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status, String message, String code, String field) {
        return ResponseEntity
                .status(status)
                .body(new ApiErrorResponse(new ApiError(message, code, field)));
    }

    private String codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            case CONFLICT -> "CONFLICT";
            case UNPROCESSABLE_ENTITY -> "UNPROCESSABLE_ENTITY";
            case SERVICE_UNAVAILABLE -> "SERVICE_UNAVAILABLE";
            default -> "ERROR";
        };
    }
}