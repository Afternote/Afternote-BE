package com.afternote.domain.auth.service;

import com.afternote.global.config.PasskeyProperties;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.util.Base64UrlUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class PasskeyChallengeService {

    public static final String TYPE_REGISTER = "REGISTER";
    public static final String TYPE_AUTH = "AUTH";

    private static final String KEY_PREFIX = "PASSKEY:CHALLENGE:";

    private final RedisTemplate<String, String> redisTemplate;
    private final PasskeyProperties passkeyProperties;

    public Challenge issue(String type, Long userId) {
        Challenge challenge = new DefaultChallenge();
        String value = type + ":" + (userId == null ? "" : userId);
        redisTemplate.opsForValue().set(
                key(challenge),
                value,
                Duration.ofSeconds(passkeyProperties.getChallengeTtlSeconds())
        );
        return challenge;
    }

    /**
     * 저장된 challenge 를 읽고 즉시 삭제한다. 없거나 타입이 다르면 2700.
     * @return REGISTER 면 userId, AUTH 면 null
     */
    public Long consume(Challenge challenge, String expectedType) {
        if (challenge == null || challenge.getValue() == null) {
            throw new CustomException(ErrorCode.PASSKEY_CHALLENGE_INVALID);
        }
        String redisKey = key(challenge);
        String stored = redisTemplate.opsForValue().get(redisKey);
        redisTemplate.delete(redisKey);
        if (stored == null || stored.isBlank()) {
            throw new CustomException(ErrorCode.PASSKEY_CHALLENGE_INVALID);
        }
        String[] parts = stored.split(":", 2);
        if (parts.length == 0 || !expectedType.equals(parts[0])) {
            throw new CustomException(ErrorCode.PASSKEY_CHALLENGE_INVALID);
        }
        if (TYPE_REGISTER.equals(expectedType)) {
            if (parts.length < 2 || parts[1].isBlank()) {
                throw new CustomException(ErrorCode.PASSKEY_CHALLENGE_INVALID);
            }
            return Long.parseLong(parts[1]);
        }
        return null;
    }

    public static String encode(Challenge challenge) {
        return Base64UrlUtil.encodeToString(challenge.getValue());
    }

    private String key(Challenge challenge) {
        return KEY_PREFIX + encode(challenge);
    }
}
