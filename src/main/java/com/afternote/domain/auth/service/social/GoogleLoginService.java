package com.afternote.domain.auth.service.social;

import com.afternote.domain.auth.dto.SocialUserInfo;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 구글 소셜 로그인 구현체.
 * 클라이언트(모바일)가 Google Sign-In SDK로 받은 ID Token(JWT)을 검증합니다.
 *
 * 검증 항목 (라이브러리가 자동 처리):
 * - 서명: Google 공개키로 RSA 검증
 * - issuer: accounts.google.com / https://accounts.google.com
 * - audience: setAudience()로 등록한 Web Client ID
 * - 만료(exp), 발행시각(iat)
 */
@Slf4j
@Service
public class GoogleLoginService implements SocialLoginService {

    private final String webClientId;
    private GoogleIdTokenVerifier verifier;

    public GoogleLoginService(@Value("${google.oauth2.web-client-id}") String webClientId) {
        this.webClientId = webClientId;
    }

    @PostConstruct
    void initVerifier() {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new com.google.api.client.http.javanet.NetHttpTransport(),
                new com.google.api.client.json.gson.GsonFactory()
        )
                .setAudience(Collections.singletonList(webClientId))
                .build();
    }

    @Override
    public SocialUserInfo getUserInfo(String idTokenString) {
        requireLooksLikeGoogleIdToken(idTokenString);
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                log.warn("Google ID Token verification failed (invalid signature/aud/iss/exp)");
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }

            GoogleIdToken.Payload payload = idToken.getPayload();

            String sub = payload.getSubject();
            if (sub == null || sub.isBlank()) {
                log.error("Google ID Token missing sub: {}", payload);
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }

            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                log.error("Google ID Token missing email (ensure email scope): {}", payload);
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }

            String name = stringValue(payload.get("name"));
            if (name == null || name.isBlank()) {
                name = email;
            }

            String picture = stringValue(payload.get("picture"));

            return SocialUserInfo.builder()
                    .providerId(sub)
                    .email(email)
                    .name(name)
                    .provider(AuthProvider.GOOGLE)
                    .profileImageUrl(picture)
                    .build();
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            // IllegalArgumentException 등: 깨진 JWT는 클라이언트 입력이므로 500이 아니라 소셜 실패(400)
            log.warn("Google ID Token verify failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
    }

    /**
     * 빈 값·JWT 형태가 아니면 라이브러리 검증 전에 400으로 거절한다.
     */
    static void requireLooksLikeGoogleIdToken(String idTokenString) {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
        String trimmed = idTokenString.trim();
        // Google ID Token 은 header.payload.signature 형태의 JWT
        String[] parts = trimmed.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    @Override
    public String getProviderName() {
        return "GOOGLE";
    }
}
