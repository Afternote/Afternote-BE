package com.afternote.domain.auth.service.social;

import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 소셜 로그인 제공자별 서비스를 선택하는 팩토리 클래스
 * 
 * 스프링이 자동으로 모든 SocialLoginService 구현체를 주입해주고,
 * provider 파라미터에 따라 적절한 서비스를 반환합니다.
 * 
 * 🎯 핵심: 새로운 소셜 로그인을 추가할 때 이 클래스도 수정할 필요 없음!
 *         스프링이 자동으로 새로운 구현체를 찾아서 Map에 등록해줍니다.
 */
@Component
@RequiredArgsConstructor
public class SocialLoginFactory {
    
    // 스프링이 모든 SocialLoginService 구현체를 List로 주입
    private final List<SocialLoginService> socialLoginServices;
    
    // getProviderName()을 키로 하는 Map으로 변환하여 캐싱
    private Map<String, SocialLoginService> serviceMap;
    
    /**
     * provider 이름으로 적절한 소셜 로그인 서비스를 반환
     * 
     * @param provider 소셜 로그인 제공자 (KAKAO, GOOGLE, NAVER 등)
     * @return 해당 제공자의 SocialLoginService 구현체
     * @throws CustomException 지원하지 않는 제공자인 경우
     */
    public SocialLoginService getService(String provider) {
        // 최초 호출시 한번만 Map 생성 (지연 초기화)
        if (serviceMap == null) {
            serviceMap = socialLoginServices.stream()
                    .collect(Collectors.toMap(
                            SocialLoginService::getProviderName,
                            Function.identity()
                    ));
        }
        
        SocialLoginService service = serviceMap.get(provider.toUpperCase());
        
        if (service == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_SOCIAL_LOGIN);
        }
        
        return service;
    }
    
    /**
     * 지원하는 모든 소셜 로그인 제공자 목록 반환
     * 
     * @return 제공자 이름 목록 (예: ["KAKAO", "GOOGLE"])
     */
    public List<String> getSupportedProviders() {
        return socialLoginServices.stream()
                .map(SocialLoginService::getProviderName)
                .collect(Collectors.toList());
    }
}
