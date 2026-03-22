package com.afternote.domain.mindrecord.controller;

import com.afternote.domain.mindrecord.service.MindRecordReceiverService;
import com.afternote.domain.mindrecord.service.MindRecordService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MindRecordControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private MindRecordController mindRecordController;

    @Mock
    private MindRecordService mindRecordService;

    @Mock
    private MindRecordReceiverService mindRecordReceiverService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(mindRecordController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("마음의 기록 목록 조회 API 성공")
    void getMindRecordList_Success() throws Exception {
        given(mindRecordService.getMindRecordList(eq(USER_ID), any())).willReturn(null);

        mockMvc.perform(get("/mind-records")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .param("view", "LIST"))
                .andExpect(status().isOk());

        verify(mindRecordService).getMindRecordList(eq(USER_ID), any());
    }

    @Test
    @DisplayName("마음의 기록 작성 API 성공")
    void createMindRecord_Success() throws Exception {
        given(mindRecordService.createMindRecord(eq(USER_ID), any())).willReturn(10L);

        mockMvc.perform(post("/mind-records")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIARY\",\"title\":\"t\",\"content\":\"c\",\"date\":\"2026-01-01\",\"isDraft\":false}"))
                .andExpect(status().isOk());

        verify(mindRecordService).createMindRecord(eq(USER_ID), any());
    }

    @Test
    @DisplayName("마음의 기록 작성 API 실패 - 필수 필드 누락")
    void createMindRecord_MissingRequiredFields_Fail() throws Exception {
        mockMvc.perform(post("/mind-records")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"DIARY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("마음의 기록 단건 조회 API 성공")
    void getMindRecordDetail_Success() throws Exception {
        given(mindRecordService.getMindRecordDetail(USER_ID, 3L)).willReturn(null);

        mockMvc.perform(get("/mind-records/{recordId}", 3L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(mindRecordService).getMindRecordDetail(USER_ID, 3L);
    }

    @Test
    @DisplayName("마음의 기록 수정 API 성공")
    void updateMindRecord_Success() throws Exception {
        mockMvc.perform(patch("/mind-records/{recordId}", 3L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(mindRecordService).updateMindRecord(eq(USER_ID), eq(3L), any());
    }

    @Test
    @DisplayName("마음의 기록 삭제 API 성공")
    void deleteMindRecord_Success() throws Exception {
        mockMvc.perform(delete("/mind-records/{recordId}", 3L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isNoContent());

        verify(mindRecordService).deleteMindRecord(USER_ID, 3L);
    }

    @Test
    @DisplayName("수신인 전달 설정 API 성공")
    void toggleMindRecordReceiver_Success() throws Exception {
        mockMvc.perform(patch("/mind-records/{recordId}/receivers/{receiverId}", 3L, 7L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        verify(mindRecordReceiverService).toggleReceiver(USER_ID, 3L, 7L, true);
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
