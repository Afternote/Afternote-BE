package com.afternote.global.jwt;

import com.afternote.domain.auth.service.TokenService;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.resolver.UserIdArgumentResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. 헤더에서 토큰 추출
        String token = resolveToken(request);

        // 2. 토큰이 있고, 유효하다면?
        if (token != null && jwtTokenProvider.validateToken(token)) {

            // 3. 토큰에서 ID(Subject) 꺼내기
            Long userId = jwtTokenProvider.getUserId(token);

            // 탈퇴/삭제된 계정 또는 강제 만료된 accessToken 은 인증하지 않는다
            if (!tokenService.isAccessRevoked(userId) && userRepository.existsById(userId)) {
                // 4. Request Attribute에 userId 저장 (컨트롤러에서 @AuthUser로 접근 가능)
                request.setAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, userId);

                // 5. 유저 정보를 임시로 만들어서 SecurityContext에 넣어주기 (로그인 인정)
                UserDetails userDetails = new User(String.valueOf(userId), "", Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")));
                Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 6. 다음 필터로 넘기기
        filterChain.doFilter(request, response);
    }

    // 헤더에서 "Bearer " 떼고 토큰만 가져오는 메서드
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}