package com.afternote.domain.auth.dto;

import com.afternote.domain.user.model.AuthProvider;
import lombok.Builder;
import lombok.Getter;

/**
 * 소셜 로그인으로부터 받아온 사용자 정보를 담는 공통 DTO
 * 
 * 각 소셜 로그인 제공자(카카오, 구글, 네이버 등)의 응답을
 * 이 공통 형식으로 변환하여 사용합니다.
 */
@Builder
public record SocialUserInfo(
        @Getter
        String providerId,

        @Getter
        String email,

        @Getter
        String name,

        @Getter
        AuthProvider provider,

        @Getter
        String profileImageUrl
) {
    
    /**
     * 소셜 로그인 제공자로부터 받은 고유 ID
     * 예: 카카오의 경우 user_id, 구글의 경우 sub
     */
    
    /**
     * 사용자 이메일
     */
    
    /**
     * 사용자 이름
     */
    
    /**
     * 소셜 로그인 제공자 타입
     * KAKAO, GOOGLE, NAVER 등
     */
    
    /**
     * 프로필 이미지 URL (선택적)
     */
}
