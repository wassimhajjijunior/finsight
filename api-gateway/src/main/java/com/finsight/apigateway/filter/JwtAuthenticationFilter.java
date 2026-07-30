package com.finsight.apigateway.filter;

import com.finsight.apigateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        // Check if this route has skip-auth metadata
        // This is how public routes (login, register) bypass JWT validation
        Boolean skipAuth = exchange.getAttribute("skip-auth");
        if (Boolean.TRUE.equals(skipAuth)) {
            return chain.filter(exchange);
        }

        // Check route metadata from gateway config
        String skipAuthMeta = exchange.getRequest().getHeaders().getFirst("skip-auth");

        // Get the path — check if it is a public path
        String path = request.getURI().getPath();
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return rejectRequest(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7); // remove "Bearer " prefix

        if (!jwtUtil.isTokenValid(token)) {
            return rejectRequest(exchange, "Invalid or expired JWT token");
        }

        // Token is valid — extract claims and inject as headers for downstream services
        Claims claims = jwtUtil.extractAllClaims(token);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Email", claims.get("email", String.class))
                .header("X-User-Role", claims.get("role", String.class))
                // Remove the Authorization header — services don't need it
                // They trust the X-User-* headers injected by Gateway
                .build();

        log.debug("JWT validated for user: {} path: {}",
                claims.getSubject(), path);

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/actuator");
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String reason) {
        log.warn("Request rejected: {}", reason);
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -2; // runs after CorrelationId (-3) but before Logging (-1)
    }
}