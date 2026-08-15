package com.afternote.domain.auth.service;

import com.afternote.domain.auth.dto.ReissueResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TokenService {

    private static final String ACCESS_REVOKED_PREFIX = "AT:REVOKED:";
    private static final String REFRESH_PREFIX = "RT:";
    private static final String REISSUE_GRACE_PREFIX = "RT:GRACE:";

    private final RedisTemplate<String, Long> redisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final long accessTokenExpirationMs;
    private final long reissueGraceSeconds;

    public TokenService(
            RedisTemplate<String, Long> redisTemplate,
            RedisTemplate<String, String> stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMs,
            @Value("${jwt.reissue-grace-seconds:30}") long reissueGraceSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.reissueGraceSeconds = reissueGraceSeconds;
    }

    // Refresh Token 저장 (예: 7일간 유효)
    public void saveToken(String refreshToken, Long userId) {
        redisTemplate.opsForValue()
                .set(REFRESH_PREFIX + refreshToken, userId, 7, TimeUnit.DAYS);
    }

    public Long getUserId(String refreshToken) {
        return redisTemplate.opsForValue().get(REFRESH_PREFIX + refreshToken);
    }

    /**
     * 원자적 조회 및 삭제 (RTR). 동시 reissue 중 하나만 RT 키를 가져간다.
     */
    public Long getUserIdAndDelete(String refreshToken) {
        return redisTemplate.opsForValue().getAndDelete(REFRESH_PREFIX + refreshToken);
    }

    public void deleteToken(String refreshToken) {
        redisTemplate.delete(REFRESH_PREFIX + refreshToken);
    }

    /**
     * 회전 직후·동시 재발급이 같은 새 토큰 쌍을 받도록 grace 캐시를 남긴다.
     */
    public void saveReissueGrace(String oldRefreshToken, ReissueResponse response) {
        if (oldRefreshToken == null || response == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            stringRedisTemplate.opsForValue().set(
                    REISSUE_GRACE_PREFIX + oldRefreshToken,
                    json,
                    reissueGraceSeconds,
                    TimeUnit.SECONDS
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize reissue grace: {}", e.getMessage());
        }
    }

    /**
     * 이미 회전된 refresh에 대한 grace 조회.
     * 승자가 아직 grace를 쓰기 전일 수 있어 짧게 재시도한다.
     */
    public ReissueResponse findReissueGrace(String oldRefreshToken) {
        if (oldRefreshToken == null) {
            return null;
        }
        String key = REISSUE_GRACE_PREFIX + oldRefreshToken;
        for (int attempt = 0; attempt < 10; attempt++) {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isBlank()) {
                try {
                    return objectMapper.readValue(json, ReissueResponse.class);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse reissue grace: {}", e.getMessage());
                    return null;
                }
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    public void deleteAllUserTokens(Long userId) {
        var keys = redisTemplate.keys(REFRESH_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                Long storedUserId = redisTemplate.opsForValue().get(key);
                if (userId.equals(storedUserId)) {
                    redisTemplate.delete(key);
                }
            }
        }
    }

    public void revokeUserAccess(Long userId) {
        redisTemplate.opsForValue().set(
                ACCESS_REVOKED_PREFIX + userId,
                userId,
                accessTokenExpirationMs,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean isAccessRevoked(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(ACCESS_REVOKED_PREFIX + userId));
    }
}
