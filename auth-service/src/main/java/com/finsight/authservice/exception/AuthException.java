package com.finsight.authservice.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public static AuthException invalidCredentials() {
        return new AuthException(
                "Invalid email or password",
                HttpStatus.UNAUTHORIZED
        );
    }

    public static AuthException emailAlreadyExists() {
        return new AuthException(
                "An account with this email already exists",
                HttpStatus.CONFLICT
        );
    }

    public static AuthException invalidRefreshToken() {
        return new AuthException(
                "Invalid or expired refresh token",
                HttpStatus.UNAUTHORIZED
        );
    }

    public static AuthException accountDisabled() {
        return new AuthException(
                "Account is disabled",
                HttpStatus.FORBIDDEN
        );
    }
}