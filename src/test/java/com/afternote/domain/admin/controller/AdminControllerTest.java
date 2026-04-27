package com.afternote.domain.admin.controller;

import com.afternote.domain.admin.service.AdminService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private AdminController adminController;

    @Mock
    private AdminService adminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("대기 인증 목록 조회 API 성공")
    void getPendingVerifications_Success() throws Exception {
        given(adminService.getPendingVerifications(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/verifications").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(adminService).getPendingVerifications(USER_ID);
    }

    @Test
    @DisplayName("인증 상세 조회 API 성공")
    void getVerificationDetail_Success() throws Exception {
        given(adminService.getVerificationDetail(USER_ID, 5L)).willReturn(null);

        mockMvc.perform(get("/api/v1/admin/verifications/{id}", 5L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(adminService).getVerificationDetail(USER_ID, 5L);
    }

    @Test
    @DisplayName("인증 상세 조회 API 실패 - 잘못된 id 타입")
    void getVerificationDetail_InvalidIdType_Fail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/verifications/not-number")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("인증 승인 API 성공")
    void approveVerification_Success() throws Exception {
        given(adminService.approveVerification(eq(USER_ID), eq(5L), any())).willReturn(null);

        mockMvc.perform(post("/api/v1/admin/verifications/{id}/approve", 5L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"ok\"}"))
                .andExpect(status().isOk());

        verify(adminService).approveVerification(eq(USER_ID), eq(5L), any());
    }

    @Test
    @DisplayName("인증 거절 API 성공")
    void rejectVerification_Success() throws Exception {
        given(adminService.rejectVerification(eq(USER_ID), eq(5L), any())).willReturn(null);

        mockMvc.perform(post("/api/v1/admin/verifications/{id}/reject", 5L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminNote\":\"reject\"}"))
                .andExpect(status().isOk());

        verify(adminService).rejectVerification(eq(USER_ID), eq(5L), any());
    }

    private static class UserIdTestArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(UserId.class)
                    && Long.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            Object userId = webRequest.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
            return userId == null ? USER_ID : userId;
        }
    }
}
