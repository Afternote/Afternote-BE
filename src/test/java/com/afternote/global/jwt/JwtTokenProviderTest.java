package com.afternote.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "01234567890123456789012345678901",
                3_600_000L,
                7 * 24 * 60 * 60 * 1000L
        );
    }

    @Test
    @DisplayName("Access Token 유효 시간(초)을 반환한다")
    void getAccessTokenExpirationSeconds() {
        assertThat(jwtTokenProvider.getAccessTokenExpirationSeconds()).isEqualTo(3600L);
    }
}
