package com.afternote.domain.auth.service.social;

import com.afternote.domain.auth.dto.SocialUserInfo;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 카카오 소셜 로그인 구현체
 * 
 * SocialLoginService를 구현하여 카카오 특화 로그인 로직을 처리합니다.
 * 새로운 소셜 로그인을 추가하려면 이 클래스를 참고하여 동일한 구조로 만들면 됩니다!
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService implements SocialLoginService {
    
    private final WebClient webClient;
    
    // application.yml에서 설정값을 가져옵니다
    // kakao.api.user-info-url: https://kapi.kakao.com/v2/user/me
    @Value("${kakao.api.user-info-url}")
    private String kakaoUserInfoUrl;
    
    @Override
    public SocialUserInfo getUserInfo(String accessToken) {
        try {
            
            log.debug("📤 카카오 API 호출: {}", kakaoUserInfoUrl);
            Map<String, Object> responseBody = webClient.get()
                    .uri(kakaoUserInfoUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();
            
            
            if (responseBody == null || responseBody.isEmpty()) {
                log.error("❌ 카카오 API 응답이 비어있습니다");
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }
            
            Object id = responseBody.get("id");
            if (id == null || String.valueOf(id).isBlank()) {
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }
            String providerId = String.valueOf(id);
            log.debug("👤 Provider ID: {}", providerId);
            
            // kakao_account 객체에서 이메일과 프로필 정보 추출
            Map<String, Object> kakaoAccount = (Map<String, Object>) responseBody.get("kakao_account");
            if (kakaoAccount == null) {
                log.error("❌ kakao_account가 응답에 없습니다. 응답: {}", responseBody);
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }
            
            String email = (String) kakaoAccount.get("email");
            // 이메일로 기존 계정을 연결하므로 카카오가 검증한 이메일만 사용한다.
            if (email != null && !email.isBlank()
                    && (!Boolean.TRUE.equals(kakaoAccount.get("is_email_valid"))
                    || !Boolean.TRUE.equals(kakaoAccount.get("is_email_verified")))) {
                email = null;
            }
            
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            if (profile == null) {
                log.error("❌ profile이 kakao_account에 없습니다. kakaoAccount: {}", kakaoAccount);
                throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
            }
            
            String nickname = (String) profile.get("nickname");
            String profileImageUrl = (String) profile.get("profile_image_url");
            log.debug("✅ 사용자 정보 추출 완료 - 닉네임: {}", nickname);
            
            return SocialUserInfo.builder()
                    .providerId(providerId)
                    .email(email)
                    .name(nickname)
                    .provider(AuthProvider.KAKAO)
                    .profileImageUrl(profileImageUrl)
                    .build();
                    
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ 카카오 사용자 정보 조회 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }
    }
    
    @Override
    public String getProviderName() {
        return "KAKAO";
    }
}
