package com.finsight.budgetservice.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

// Single structured error shape shared by every FinSight service:
// {"detail": {"message": "...", "code": "...", "field": "..."}}
// `field` is only present for field-level errors (e.g. validation).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String message, String code, String field) {
}