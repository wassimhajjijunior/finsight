package com.finsight.authservice.exception;

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

/**
 * Central exception contract for auth-service.
 *
 * Every error leaves this service in the same shape:
 * {"detail": {"message": "...", "code": "...", "field": "..."}}
 *
 * Handlers:
 * - AuthException (domain errors)                    -> its status + dedicated code
 * - Validation (body, params, headers, payload)      -> 400 VALIDATION_ERROR
 * - Unexpected errors                                -> safe generic 500, no internals leaked
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(
            AuthException ex) {

        log.warn("Auth error ({}): {}", ex.getCode(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), ex.getCode(), null);
    }

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
}