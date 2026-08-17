package com.afternote.domain.auth.controller;

import com.afternote.domain.auth.dto.LoginResponse;
import com.afternote.domain.auth.dto.PasskeyCreationOptionsResponse;
import com.afternote.domain.auth.dto.PasskeyRequestOptionsResponse;
import com.afternote.domain.auth.dto.PasskeyResponse;
import com.afternote.domain.auth.service.PasskeyService;
import com.afternote.global.resolver.UserId;
import com.afternote.global.resolver.UserIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasskeyAuthControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private PasskeyAuthController passkeyAuthController;

    @Mock
    private PasskeyService passkeyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(passkeyAuthController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("등록 옵션 API")
    void registerOptions() throws Exception {
        given(passkeyService.registerOptions(USER_ID)).willReturn(
                PasskeyCreationOptionsResponse.builder()
                        .challenge("abc")
                        .timeout(300000L)
                        .attestation("none")
                        .excludeCredentials(List.of())
                        .build()
        );

        mockMvc.perform(post("/api/v1/auth/passkey/register/options")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.challenge").value("abc"));
    }

    @Test
    @DisplayName("인증 옵션 API 는 비로그인")
    void authenticateOptions() throws Exception {
        given(passkeyService.authenticateOptions()).willReturn(
                PasskeyRequestOptionsResponse.builder()
                        .challenge("xyz")
                        .rpId("afternote.kro.kr")
                        .allowCredentials(List.of())
                        .userVerification("required")
                        .build()
        );

        mockMvc.perform(post("/api/v1/auth/passkey/authenticate/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rpId").value("afternote.kro.kr"));
    }

    @Test
    @DisplayName("인증 검증 성공 시 로그인 봉투")
    void authenticate() throws Exception {
        given(passkeyService.authenticate(any())).willReturn(
                LoginResponse.builder()
                        .accessToken("a")
                        .refreshToken("r")
                        .expiresIn(3600)
                        .build()
        );

        mockMvc.perform(post("/api/v1/auth/passkey/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"cred\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("a"))
                .andExpect(jsonPath("$.data.refreshToken").value("r"));
    }

    @Test
    @DisplayName("등록 검증 API")
    void register() throws Exception {
        given(passkeyService.register(eq(USER_ID), any())).willReturn(
                PasskeyResponse.builder()
                        .id(9L)
                        .displayName("패스키")
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        mockMvc.perform(post("/api/v1/auth/passkey/register")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"cred\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));
        verify(passkeyService).register(eq(USER_ID), any());
    }

    private static class UserIdTestArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(UserId.class);
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            return webRequest.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
        }
    }
}
