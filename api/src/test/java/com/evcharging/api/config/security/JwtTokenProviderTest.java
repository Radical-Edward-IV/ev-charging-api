package com.evcharging.api.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                "my-super-secret-key-for-ev-charging-api-that-is-at-least-256-bits-long",
                3600000L);
    }

    @Test
    void generateToken_and_extractClaims() {
        String token = provider.generateToken("test@example.com", "USER");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getEmail(token)).isEqualTo("test@example.com");
        assertThat(provider.getRole(token)).isEqualTo("USER");
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertThat(provider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                "my-super-secret-key-for-ev-charging-api-that-is-at-least-256-bits-long",
                -1000L); // 이미 만료
        String token = shortLived.generateToken("test@example.com", "USER");

        assertThat(provider.validateToken(token)).isFalse();
    }
}
