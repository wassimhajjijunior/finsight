package com.finsight.authservice.service;

import com.finsight.authservice.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;
    private final StringRedisTemplate redisTemplate;

    public TokenService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-expiration}") long refreshTokenExpiration,
            StringRedisTemplate redisTemplate) {

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Generate a short-lived access token.
     * Contains user identity claims read by the Gateway.
     */
    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("firstName", user.getFirstName())
                .issuedAt(new Date())
                .expiration(new Date(
                        System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate a long-lived refresh token.
     * Stored in Redis — can be revoked instantly on logout.
     * The token itself is a random UUID — no user data inside.
     * We look up the user ID from Redis when refreshing.
     */
    public String generateRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        String redisKey = "refresh:" + token;

        // Store userId in Redis with TTL matching token expiration
        redisTemplate.opsForValue().set(
                redisKey,
                user.getId().toString(),
                refreshTokenExpiration,
                TimeUnit.MILLISECONDS
        );

        log.debug("Refresh token stored in Redis for userId={}",
                user.getId());
        return token;
    }

    /**
     * Validate a refresh token by checking Redis.
     * Returns the userId if valid, null if expired or blacklisted.
     */
    public String validateRefreshToken(String token) {
        String redisKey = "refresh:" + token;
        return redisTemplate.opsForValue().get(redisKey);
    }

    /**
     * Revoke a refresh token on logout.
     * Deleting from Redis means the next refresh attempt will fail.
     */
    public void revokeRefreshToken(String token) {
        String redisKey = "refresh:" + token;
        redisTemplate.delete(redisKey);
        log.debug("Refresh token revoked");
    }

    /**
     * Parse claims from an access token.
     * Used internally — Gateway does its own parsing.
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}