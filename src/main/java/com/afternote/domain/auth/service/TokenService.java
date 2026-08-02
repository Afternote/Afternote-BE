package com.afternote.domain.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private static final String ACCESS_REVOKED_PREFIX = "AT:REVOKED:";

    private final RedisTemplate<String, Long> redisTemplate;
    private final long accessTokenExpirationMs;

    public TokenService(
            RedisTemplate<String, Long> redisTemplate,
            @Value("${jwt.access-token-expiration}") long accessTokenExpirationMs
    ) {
        this.redisTemplate = redisTemplate;
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    // Refresh Token 저장 (예: 7일간 유효)
    // Key를 "RT:사용자ID" 또는 "RT:이메일" 형태로 지정하여 구분하기 쉽게 함
    public void saveToken(String refreshToken, Long userId) {
        redisTemplate.opsForValue()
                     .set("RT:"+refreshToken,userId, 7, TimeUnit.DAYS);
    }

    //조회
    public Long getUserId(String refreshToken) {
        return redisTemplate.opsForValue().get("RT:" + refreshToken);
    }
    
    // 원자적 조회 및 삭제 (TOCTOU 방지)
    // reissue 시 동시성 문제를 방지하기 위해 사용
    public Long getUserIdAndDelete(String refreshToken) {
        return redisTemplate.opsForValue().getAndDelete("RT:" + refreshToken);
    }
    
    // 로그아웃 시 Refresh Token 삭제
    public void deleteToken(String refreshToken) {
        redisTemplate.delete("RT:" + refreshToken);
    }
    
    // 회원탈퇴 시 해당 유저의 모든 Refresh Token 삭제
    public void deleteAllUserTokens(Long userId) {
        // RT:* 패턴의 모든 키를 조회
        var keys = redisTemplate.keys("RT:*");
        if (keys != null && !keys.isEmpty()) {
            for (String key : keys) {
                Long storedUserId = redisTemplate.opsForValue().get(key);
                if (userId.equals(storedUserId)) {
                    redisTemplate.delete(key);
                }
            }
        }
    }

    /**
     * 탈퇴/강제 만료 시 accessToken 을 즉시 무효화한다.
     * TTL 은 access token 최대 수명과 동일하게 둔다.
     */
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
