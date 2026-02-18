package com.ledgerxlite.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT token lifecycle management.
 *
 * Generates signed tokens on login, validates them on every request.
 * Secret and expiry are externalized to application properties.
 *
 * Token contains:
 *  - subject  : user email
 *  - userId   : user's database ID (custom claim)
 *  - iat      : issued-at
 *  - exp      : expiry
 */
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long      expiryMs;

    public JwtTokenProvider(
            @Value("${ledgerx.jwt.secret}") String secret,
            @Value("${ledgerx.jwt.expiry-ms:86400000}") long expiryMs) {

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                "ledgerx.jwt.secret must be at least 32 characters");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMs  = expiryMs;
    }

    /** Generate a signed JWT for the given user. */
    public String generateToken(Long userId, String email) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** Extract the email (subject) from a valid token. */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /** Extract the userId claim from a valid token. */
    public Long extractUserId(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /**
     * Validate token signature and expiry.
     * Returns false instead of throwing — caller decides how to respond.
     */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
