package com.ledgerxlite.security;

import com.ledgerxlite.domain.User;
import com.ledgerxlite.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Per-request JWT authentication filter.
 *
 * FLOW:
 *   1. Extract "Authorization: Bearer <token>" header
 *   2. Validate token signature and expiry via JwtTokenProvider
 *   3. Load user from database (verifies user still exists and is ACTIVE)
 *   4. Set UsernamePasswordAuthenticationToken into SecurityContext
 *   5. Continue filter chain
 *
 * WHY load user from DB on every request:
 *   Tokens are stateless and cannot be revoked. Loading the user lets us
 *   reject tokens belonging to SUSPENDED or CLOSED accounts without
 *   maintaining a token blacklist. The cost is one DB read per request,
 *   which is acceptable for a financial API (and can be cached if needed).
 *
 * WHY OncePerRequestFilter:
 *   Guarantees the filter runs exactly once per request even in forward/
 *   include scenarios. Critical for idempotency of authentication state.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider tokenProvider;
    private final UserRepository   userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   UserRepository   userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        
        log.debug("🔒 Authentication filter: {} {}", method, requestUri);

        String token = extractBearerToken(request);

        if (StringUtils.hasText(token)) {
            log.debug("🔑 JWT token found, validating...");
            
            if (tokenProvider.isValid(token)) {
                String email = tokenProvider.extractEmail(token);
                Long userId = tokenProvider.extractUserId(token);
                
                log.debug("✓ Token valid for user: email={}, userId={}", email, userId);
                
                // Add userId to MDC for logging context
                MDC.put("userId", userId.toString());
                MDC.put("userEmail", email);

                userRepository.findByEmail(email).ifPresent(user -> {
                    if (user.getStatus() == User.UserStatus.ACTIVE) {
                        var auth = new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        log.info("✓ User authenticated successfully - userId={}, email={}, status={}", 
                                user.getId(), email, user.getStatus());
                    } else {
                        log.warn("✗ Authentication rejected - user status is {} (not ACTIVE) - userId={}, email={}", 
                                user.getStatus(), user.getId(), email);
                    }
                });
            } else {
                log.warn("✗ Invalid JWT token - signature or expiry check failed");
            }
        } else {
            log.debug("No JWT token in request (public endpoint or unauthenticated)");
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Clean up MDC
            MDC.remove("userId");
            MDC.remove("userEmail");
        }
    }

    /**
     * Extract the raw token from "Authorization: Bearer <token>".
     * Returns null if the header is absent or malformed.
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).strip();
        }
        return null;
    }
}
