package com.ledgerxlite.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ledgerxlite.security.JwtTokenProvider;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
            "test-secret-key-that-is-long-enough!!", 3_600_000L);
    }

    @Test @DisplayName("generateToken → isValid returns true")
    void generateAndValidate() {
        String token = provider.generateToken(1L, "user@test.com");
        assertThat(provider.isValid(token)).isTrue();
    }

    @Test @DisplayName("extractEmail returns correct subject")
    void extractEmail() {
        String token = provider.generateToken(42L, "alice@test.com");
        assertThat(provider.extractEmail(token)).isEqualTo("alice@test.com");
    }

    @Test @DisplayName("extractUserId returns correct claim")
    void extractUserId() {
        String token = provider.generateToken(99L, "bob@test.com");
        assertThat(provider.extractUserId(token)).isEqualTo(99L);
    }

    @Test @DisplayName("tampered token → isValid returns false")
    void tamperedToken() {
        String token = provider.generateToken(1L, "user@test.com");
        assertThat(provider.isValid(token + "x")).isFalse();
    }

    @Test @DisplayName("null token → isValid returns false")
    void nullToken() {
        assertThat(provider.isValid(null)).isFalse();
    }

    @Test @DisplayName("expired token → isValid returns false")
    void expiredToken() {
        var expired = new JwtTokenProvider("test-secret-key-that-is-long-enough!!", -1L);
        String token = expired.generateToken(1L, "user@test.com");
        assertThat(expired.isValid(token)).isFalse();
    }

    @Test @DisplayName("short secret < 32 chars → IllegalStateException on construction")
    void shortSecret() {
        assertThatThrownBy(() -> new JwtTokenProvider("tooshort", 3600L))
            .isInstanceOf(IllegalStateException.class);
    }
}
