package com.finsight.authservice.service;

import com.finsight.authservice.dto.*;
import com.finsight.authservice.entity.User;
import com.finsight.authservice.exception.AuthException;
import com.finsight.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Register a new user.
     * Steps:
     * 1. Check email is not already taken
     * 2. Hash the password — never store plaintext
     * 3. Save user to database
     * 4. Issue tokens immediately — user is logged in after registration
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Check for duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AuthException.emailAlreadyExists();
        }

        // Build user entity — password is hashed before saving
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(User.Role.USER)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: userId={} email={}",
                user.getId(), user.getEmail());

        return buildAuthResponse(user);
    }

    /**
     * Authenticate an existing user.
     * Steps:
     * 1. Find user by email
     * 2. Verify password matches the stored hash
     * 3. Check account is not disabled
     * 4. Issue tokens
     *
     * Security note: always return the same error for wrong email
     * and wrong password — never tell the client which one was wrong.
     * This prevents user enumeration attacks.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        // Find user — same error whether email or password is wrong
        User user = userRepository.findByEmail(
                        request.getEmail().toLowerCase().trim())
                .orElseThrow(AuthException::invalidCredentials);

        // Verify password against BCrypt hash
        if (!passwordEncoder.matches(request.getPassword(),
                user.getPassword())) {
            throw AuthException.invalidCredentials();
        }

        // Check account status
        if (!user.isEnabled()) {
            throw AuthException.accountDisabled();
        }

        log.info("User logged in: userId={}", user.getId());
        return buildAuthResponse(user);
    }

    /**
     * Issue new accessToken using a valid refreshToken.
     * Steps:
     * 1. Validate refreshToken exists in Redis (not expired, not revoked)
     * 2. Load user from DB to get latest role/status
     * 3. Revoke old refreshToken — rotation prevents replay attacks
     * 4. Issue new accessToken + new refreshToken
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshRequest request) {

        // Validate refresh token against Redis
        String userId = tokenService.validateRefreshToken(
                request.getRefreshToken());

        if (userId == null) {
            throw AuthException.invalidRefreshToken();
        }

        // Load fresh user data — role may have changed since token was issued
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(AuthException::invalidRefreshToken);

        if (!user.isEnabled()) {
            throw AuthException.accountDisabled();
        }

        // Rotate refresh token — old one is revoked, new one issued
        // This means a stolen refresh token can only be used once
        tokenService.revokeRefreshToken(request.getRefreshToken());

        log.info("Token refreshed for userId={}", user.getId());
        return buildAuthResponse(user);
    }

    /**
     * Logout — revoke the refresh token.
     * The access token will expire naturally (max 15 minutes).
     * There is no way to revoke an access token early without
     * adding a blacklist check on every request — we accept this
     * tradeoff in exchange for Gateway performance.
     */
    public void logout(String refreshToken) {
        tokenService.revokeRefreshToken(refreshToken);
        log.info("User logged out — refresh token revoked");
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(900) // 15 minutes in seconds
                .userId(user.getId().toString())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}