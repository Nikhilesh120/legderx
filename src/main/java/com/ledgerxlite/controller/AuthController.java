package com.ledgerxlite.controller;

import com.ledgerxlite.domain.User;
import com.ledgerxlite.repository.UserRepository;
import com.ledgerxlite.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Authentication controller.
 *
 * POST /auth/login  — validates credentials, returns a signed JWT.
 *
 * The JWT must be included in subsequent requests as:
 *   Authorization: Bearer <token>
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Login and token management")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository    userRepository;
    private final PasswordEncoder   passwordEncoder;
    private final JwtTokenProvider  tokenProvider;

    public AuthController(UserRepository   userRepository,
                          PasswordEncoder  passwordEncoder,
                          JwtTokenProvider tokenProvider) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider   = tokenProvider;
    }

    // ── Request / Response DTOs (local, no domain leakage) ───────────────────

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank        String password) {}

    public record LoginResponse(
            String  token,
            String  email,
            Long    userId,
            Instant expiresApprox) {}

    // ── Endpoint ──────────────────────────────────────────────────────────────

    /**
     * Authenticate user and return a signed JWT.
     *
     * HTTP Contract:
     *  200 OK          → credentials valid, token returned
     *  401 Unauthorized → wrong email or password (same message intentionally)
     *  409 Conflict    → account is not ACTIVE (SUSPENDED or CLOSED)
     */
    @PostMapping("/login")
    @Operation(
        summary     = "Login",
        description = "Validate credentials and receive a Bearer JWT token.")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("Login attempt: email={}", request.email());
        
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> {
                    log.warn("Login failed: user not found - email={}", request.email());
                    return new NoSuchElementException("Invalid email or password");
                });

        // Verify password hash
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Login failed: password mismatch - email={}, userId={}", request.email(), user.getId());
            throw new NoSuchElementException("Invalid email or password");
        }
        log.debug("Password verification successful for userId={}", user.getId());

        // Account must be ACTIVE
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            log.warn("Login failed: account not active - email={}, userId={}, status={}", 
                    request.email(), user.getId(), user.getStatus());
            throw new IllegalStateException(
                "Account is " + user.getStatus() + " and cannot authenticate");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());
        log.info("✓ Login successful - userId={}, email={}", user.getId(), request.email());

        return ResponseEntity.ok(new LoginResponse(
                token,
                user.getEmail(),
                user.getId(),
                Instant.now().plusMillis(86_400_000L) // approximate — matches default expiry
        ));
    }
}
