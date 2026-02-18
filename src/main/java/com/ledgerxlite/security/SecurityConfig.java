package com.ledgerxlite.security;

import com.ledgerxlite.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * ENDPOINT ACCESS RULES:
 *
 *   PUBLIC  (no token required):
 *     POST /api/auth/login       — obtain JWT
 *     POST /api/users/register   — create account
 *     GET  /api/actuator/health  — health probe
 *     GET  /api/swagger-ui/**    — Swagger UI
 *     GET  /api/v3/api-docs/**   — OpenAPI spec
 *
 *   PROTECTED (valid JWT required):
 *     Everything else
 *
 * SESSION: Stateless. No HTTP session is created.
 * CSRF:    Disabled — API-only, no browser form submissions.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository   userRepository;

    public SecurityConfig(JwtTokenProvider tokenProvider,
                          UserRepository   userRepository) {
        this.tokenProvider  = tokenProvider;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Auth endpoint — must be public
                .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                // Registration — public
                .requestMatchers(HttpMethod.POST, "/users/register").permitAll()
                // Health + metrics — internal probes
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Swagger UI — open for review (restrict in prod if needed)
                .requestMatchers(
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(
                new JwtAuthenticationFilter(tokenProvider, userRepository),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
