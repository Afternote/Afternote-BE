package com.afternote.domain.auth.controller;

import com.afternote.domain.auth.dto.LoginResponse;
import com.afternote.domain.auth.dto.ReissueResponse;
import com.afternote.domain.auth.dto.SocialLoginResponse;
import com.afternote.domain.auth.service.AuthService;
import com.afternote.domain.user.model.User;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("회원가입 API 성공")
    void signUp_Success() throws Exception {
        User user = org.mockito.Mockito.mock(User.class);
        given(user.getId()).willReturn(1L);
        given(user.getEmail()).willReturn("test@test.com");
        given(authService.signup(any())).willReturn(user);

        String requestBody = """
                {
                  "email": "test@test.com",
                  "password": "Password1!",
                  "name": "tester"
                }
                """;

        mockMvc.perform(post("/auth/sign-up")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.email").value("test@test.com"));

        verify(authService).signup(any());
    }

    @Test
    @DisplayName("로그인 API 성공")
    void login_Success() throws Exception {
        given(authService.login(any()))
                .willReturn(LoginResponse.builder().accessToken("access").refreshToken("refresh").build());

        String requestBody = """
                {
                  "email": "test@test.com",
                  "password": "Password1!"
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("access"));

        verify(authService).login(any());
    }

                @Test
                @DisplayName("로그인 API 실패 - 이메일 형식 오류")
                void login_InvalidEmail_Fail() throws Exception {
                                String requestBody = """
                                                                {
                                                                        "email": "invalid-email",
                                                                        "password": "Password1!"
                                                                }
                                                                """;

                                mockMvc.perform(post("/auth/login")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content(requestBody))
                                                                .andExpect(status().isBadRequest());
                }

    @Test
    @DisplayName("토큰 재발급 API 성공")
    void reissue_Success() throws Exception {
        given(authService.reissue(any()))
                .willReturn(ReissueResponse.builder().accessToken("new-access").refreshToken("new-refresh").build());

        String requestBody = """
                {
                  "refreshToken": "refresh-token"
                }
                """;

        mockMvc.perform(post("/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));

        verify(authService).reissue(any());
    }

    @Test
    @DisplayName("비밀번호 변경 API 성공")
    void passwordChange_Success() throws Exception {
        String requestBody = """
                {
                  "currentPassword": "Password1!",
                  "newPassword": "Password2@"
                }
                """;

        mockMvc.perform(post("/auth/password/change")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(authService).passwordChange(eq(USER_ID), any());
    }

    @Test
    @DisplayName("로그아웃 API 성공")
    void logout_Success() throws Exception {
        String requestBody = """
                {
                  "refreshToken": "refresh-token"
                }
                """;

        mockMvc.perform(post("/auth/logout")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(authService).logout(eq(USER_ID), any());
    }

    @Test
    @DisplayName("이메일 인증번호 전송 API 성공")
    void emailSend_Success() throws Exception {
        String requestBody = """
                {
                  "email": "test@test.com"
                }
                """;

        mockMvc.perform(post("/auth/email/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(authService).emailSend(any());
    }

    @Test
    @DisplayName("이메일 인증번호 검증 API 성공")
    void emailVerify_Success() throws Exception {
        String requestBody = """
                {
                  "email": "test@test.com",
                  "certificateCode": "123456"
                }
                """;

        mockMvc.perform(post("/auth/email/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(authService).emailVerify(any());
    }

    @Test
    @DisplayName("소셜 로그인 API 성공")
    void socialLogin_Success() throws Exception {
        given(authService.socialLogin(any()))
                .willReturn(SocialLoginResponse.builder()
                        .accessToken("social-access")
                        .refreshToken("social-refresh")
                        .isNewUser(false)
                        .build());

        String requestBody = """
                {
                  "provider": "KAKAO",
                  "accessToken": "kakao-token"
                }
                """;

        mockMvc.perform(post("/auth/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("social-access"));

        verify(authService).socialLogin(any());
    }

    private static class UserIdTestArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(UserId.class)
                    && Long.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory
        ) {
            Object userId = webRequest.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
            return userId == null ? USER_ID : userId;
        }
    }
}
