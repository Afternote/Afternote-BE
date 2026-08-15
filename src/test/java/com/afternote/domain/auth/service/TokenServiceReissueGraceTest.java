package com.afternote.domain.auth.service;

import com.afternote.domain.auth.dto.ReissueResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenServiceReissueGraceTest {

    @Mock
    private RedisTemplate<String, Long> redisTemplate;

    @Mock
    private RedisTemplate<String, String> stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> stringValueOps;

    private TokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(
                redisTemplate,
                stringRedisTemplate,
                objectMapper,
                3_600_000L,
                30L
        );
    }

    @Test
    @DisplayName("grace 저장 후 조회로 동일 응답 복원")
    void saveAndFindGrace() throws Exception {
        given(stringRedisTemplate.opsForValue()).willReturn(stringValueOps);

        ReissueResponse response = ReissueResponse.builder()
                .accessToken("a")
                .refreshToken("r")
                .expiresIn(3600L)
                .build();

        tokenService.saveReissueGrace("old", response);

        verify(stringValueOps).set(
                eq("RT:GRACE:old"),
                contains("\"accessToken\":\"a\""),
                eq(30L),
                eq(TimeUnit.SECONDS)
        );

        given(stringValueOps.get("RT:GRACE:old"))
                .willReturn(objectMapper.writeValueAsString(response));

        ReissueResponse found = tokenService.findReissueGrace("old");
        assertThat(found.getAccessToken()).isEqualTo("a");
        assertThat(found.getRefreshToken()).isEqualTo("r");
        assertThat(found.getExpiresIn()).isEqualTo(3600L);
    }
}
