package com.afternote.domain.auth.service;

import com.afternote.global.config.PasskeyProperties;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.webauthn4j.data.client.challenge.Challenge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasskeyChallengeServiceTest {

    @Mock
    RedisTemplate<String, String> redisTemplate;
    @Mock
    ValueOperations<String, String> valueOperations;

    PasskeyChallengeService service;

    @BeforeEach
    void setUp() {
        PasskeyProperties properties = new PasskeyProperties();
        properties.setChallengeTtlSeconds(300);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        service = new PasskeyChallengeService(redisTemplate, properties);
    }

    @Test
    @DisplayName("REGISTER challenge 를 Redis 에 TTL 과 함께 저장한다")
    void issueRegisterStoresValue() {
        Challenge challenge = service.issue(PasskeyChallengeService.TYPE_REGISTER, 12L);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(key.capture(), value.capture(), eq(Duration.ofSeconds(300)));
        assertThat(key.getValue()).startsWith("PASSKEY:CHALLENGE:");
        assertThat(value.getValue()).isEqualTo("REGISTER:12");
        assertThat(challenge.getValue()).isNotEmpty();
    }

    @Test
    @DisplayName("없으면 2700")
    void consumeMissing() {
        given(valueOperations.get(any())).willReturn(null);
        Challenge challenge = service.issue(PasskeyChallengeService.TYPE_AUTH, null);

        assertThatThrownBy(() -> service.consume(challenge, PasskeyChallengeService.TYPE_AUTH))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PASSKEY_CHALLENGE_INVALID);
    }
}
