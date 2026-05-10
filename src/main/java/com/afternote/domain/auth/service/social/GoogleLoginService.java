package com.afternote.domain.auth.service.social;

import com.afternote.domain.auth.dto.SocialUserInfo;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 구글 소셜 로그인 구현체.
 * 클라이언트가 받은 Google OAuth2 access token으로 userinfo를 조회합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleLoginService implements SocialLoginService {

    private final RestTemplate restTemplate;

    @Value("${google.oauth2.user-info-url}")
    private String googleUserInfoUrl;

    @Override
    public SocialUserInfo getUserInfo(String accessToken) {
        try {
            log.debug(
                    "Google userinfo request, token prefix: {}...",
                    accessToken.substring(0, Math.min(20, accessToken.length()))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    googleUserInfoUrl,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null || body.isEmpty()) {
                log.error("Google userinfo response body is empty");
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }

            String sub = stringValue(body.get("sub"));
            if (sub == null || sub.isBlank()) {
                log.error("Google userinfo missing sub: {}", body);
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }

            String email = stringValue(body.get("email"));
            if (email == null || email.isBlank()) {
                log.error("Google userinfo missing email (ensure email scope): {}", body);
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }

            String name = stringValue(body.get("name"));
            if (name == null || name.isBlank()) {
                name = email;
            }

            String picture = stringValue(body.get("picture"));

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
            log.error("Google userinfo failed: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
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
