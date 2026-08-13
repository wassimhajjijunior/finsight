package com.finsight.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                // Disable CSRF — we use JWT, not cookies
                // CSRF protects against cookie-based auth attacks
                // JWT in Authorization header is not vulnerable to CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless — no HttpSession created or used
                // Each request must carry its own authentication
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // All endpoints in this service are public
                // JWT validation happens at the Gateway — not here
                // This service only issues tokens, it does not validate them
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with cost factor 12
        // Higher cost = slower hashing = harder brute force
        // 12 is the recommended production value
        // Takes ~250ms per hash — acceptable for login, irrelevant for normal requests
        return new BCryptPasswordEncoder(12);
    }
}