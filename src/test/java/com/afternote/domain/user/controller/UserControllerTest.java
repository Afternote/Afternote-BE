package com.afternote.domain.user.controller;

import com.afternote.domain.user.service.UserService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private UserController userController;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("내 프로필 조회 API 성공")
    void getMyProfile_Success() throws Exception {
        given(userService.getMyProfile(USER_ID)).willReturn(null);

        mockMvc.perform(get("/users/me").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(userService).getMyProfile(USER_ID);
    }

    @Test
    @DisplayName("프로필 수정 API 성공")
    void updateMyProfile_Success() throws Exception {
        given(userService.updateMyProfile(eq(USER_ID), any())).willReturn(null);

        mockMvc.perform(patch("/users/me")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"tester\"}"))
                .andExpect(status().isOk());

        verify(userService).updateMyProfile(eq(USER_ID), any());
    }

    @Test
    @DisplayName("푸시 알림 설정 조회 API 성공")
    void getMyPushSettings_Success() throws Exception {
        given(userService.getMyPushSettings(USER_ID)).willReturn(null);

        mockMvc.perform(get("/users/push-settings").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(userService).getMyPushSettings(USER_ID);
    }

    @Test
    @DisplayName("연결 계정 조회 API 성공")
    void getConnectedAccounts_Success() throws Exception {
        given(userService.getConnectedAccounts(USER_ID)).willReturn(null);

        mockMvc.perform(get("/users/connected-accounts").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(userService).getConnectedAccounts(USER_ID);
    }

    @Test
    @DisplayName("푸시 알림 설정 수정 API 성공")
    void updateMyPushSettings_Success() throws Exception {
        given(userService.updateMyPushSettings(eq(USER_ID), any())).willReturn(null);

        mockMvc.perform(patch("/users/push-settings")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeLetter\":true,\"mindRecord\":false,\"afterNote\":true}"))
                .andExpect(status().isOk());

        verify(userService).updateMyPushSettings(eq(USER_ID), any());
    }

    @Test
    @DisplayName("수신인 목록 조회 API 성공")
    void getReceivers_Success() throws Exception {
        given(userService.getReceivers(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/users/receivers").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(userService).getReceivers(USER_ID);
    }

    @Test
    @DisplayName("수신자 등록 API 성공")
    void createReceiver_Success() throws Exception {
        given(userService.createReceiver(eq(USER_ID), any())).willReturn(null);

        mockMvc.perform(post("/users/receivers")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"kim\",\"relation\":\"DAUGHTER\",\"phone\":\"010\",\"email\":\"a@a.com\"}"))
                .andExpect(status().isOk());

        verify(userService).createReceiver(eq(USER_ID), any());
    }

    @Test
    @DisplayName("수신자 등록 API 실패 - 필수 name 누락")
    void createReceiver_MissingName_Fail() throws Exception {
        mockMvc.perform(post("/users/receivers")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"relation\":\"DAUGHTER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("수신인 상세 조회 API 성공")
    void getReceiverDetail_Success() throws Exception {
        given(userService.getReceiverDetail(USER_ID, 2L)).willReturn(null);

        mockMvc.perform(get("/users/receivers/{receiverId}", 2L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(userService).getReceiverDetail(USER_ID, 2L);
    }

    @Test
    @DisplayName("수신자 메시지 수정 API 성공")
    void updateReceiverMessage_Success() throws Exception {
        mockMvc.perform(patch("/users/receivers/{receiverId}/message", 2L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hi\"}"))
                .andExpect(status().isOk());

        verify(userService).updateReceiverMessage(eq(USER_ID), eq(2L), any());
    }

    @Test
    @DisplayName("전달 조건 조회 API 성공")
    void getDeliveryCondition_Success() throws Exception {
        given(userService.getDeliveryCondition(USER_ID)).willReturn(null);

        mockMvc.perform(get("/users/delivery-condition").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(userService).getDeliveryCondition(USER_ID);
    }

    @Test
    @DisplayName("전달 조건 수정 API 성공")
    void updateDeliveryCondition_Success() throws Exception {
        given(userService.updateDeliveryCondition(eq(USER_ID), any())).willReturn(null);

        mockMvc.perform(patch("/users/delivery-condition")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conditionType\":\"INACTIVITY\",\"inactivityPeriodDays\":365}"))
                .andExpect(status().isOk());

        verify(userService).updateDeliveryCondition(eq(USER_ID), any());
    }

    @Test
    @DisplayName("회원 탈퇴 API 성공")
    void deleteAccount_Success() throws Exception {
        mockMvc.perform(delete("/users/me").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(userService).deleteAccount(USER_ID);
    }

    @Test
    @DisplayName("수신인 정보 수정 API 성공")
    void updateReceiver_Success() throws Exception {
        given(userService.updateReceiver(eq(USER_ID), eq(2L), any())).willReturn(null);

        mockMvc.perform(patch("/users/receivers/{receiverId}", 2L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"kim\",\"phone\":\"010-1111-2222\",\"relation\":\"DAUGHTER\",\"email\":\"a@a.com\"}"))
                .andExpect(status().isOk());

        verify(userService).updateReceiver(eq(USER_ID), eq(2L), any());
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
