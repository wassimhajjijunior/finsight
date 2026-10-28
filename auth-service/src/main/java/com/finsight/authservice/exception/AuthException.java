package com.finsight.authservice.exception;

import org.springframework.http.HttpStatus;
import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AuthException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AuthException invalidCredentials() {
        return new AuthException(
                "Invalid email or password",
                HttpStatus.UNAUTHORIZED,
                "INVALID_CREDENTIALS"
        );
    }

    public static AuthException emailAlreadyExists() {
        return new AuthException(
                "An account with this email already exists",
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_EXISTS"
        );
    }

    public static AuthException invalidRefreshToken() {
        return new AuthException(
                "Invalid or expired refresh token",
                HttpStatus.UNAUTHORIZED,
                "INVALID_REFRESH_TOKEN"
        );
    }

    public static AuthException accountDisabled() {
        return new AuthException(
                "Account is disabled",
                HttpStatus.FORBIDDEN,
                "ACCOUNT_DISABLED"
        );
    }
}