package com.afternote.domain.afternote.controller;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.service.AfternoteService;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.exception.GlobalExceptionHandler;
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
class AfternoteControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private AfternoteController afternoteController;

    @Mock
    private AfternoteService afternoteService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(afternoteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("애프터노트 목록 조회 API 성공")
    void getAfternotes_Success() throws Exception {
        given(afternoteService.getAfternotes(USER_ID, AfternoteCategoryType.SOCIAL, 0, 10)).willReturn(null);

        mockMvc.perform(get("/api/afternotes")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .param("category", "SOCIAL")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk());

        verify(afternoteService).getAfternotes(USER_ID, AfternoteCategoryType.SOCIAL, 0, 10);
    }

    @Test
    @DisplayName("애프터노트 목록 조회 API 실패 - 잘못된 category")
    void getAfternotes_InvalidCategory_Fail() throws Exception {
        mockMvc.perform(get("/api/afternotes")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .param("category", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애프터노트 상세 조회 API 성공")
    void getDetailAfternote_Success() throws Exception {
        given(afternoteService.getDetailAfternote(USER_ID, 3L)).willReturn(null);

        mockMvc.perform(get("/api/afternotes/{afternoteId}", 3L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(afternoteService).getDetailAfternote(USER_ID, 3L);
    }

    @Test
    @DisplayName("애프터노트 상세 조회 API 실패 - 잘못된 afternoteId 타입")
    void getDetailAfternote_InvalidIdType_Fail() throws Exception {
        mockMvc.perform(get("/api/afternotes/not-number")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애프터노트 생성 API 성공")
    void createAfternote_Success() throws Exception {
        given(afternoteService.createAfternote(eq(USER_ID), any())).willReturn(null);

        mockMvc.perform(post("/api/afternotes")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SOCIAL\",\"title\":\"title\"}"))
                .andExpect(status().isOk());

        verify(afternoteService).createAfternote(eq(USER_ID), any());
    }

    @Test
    @DisplayName("애프터노트 생성 API 실패 - title 누락")
    void createAfternote_MissingTitle_Fail() throws Exception {
        mockMvc.perform(post("/api/afternotes")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SOCIAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애프터노트 생성 API 실패 - category 누락")
    void createAfternote_MissingCategory_Fail() throws Exception {
        mockMvc.perform(post("/api/afternotes")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"title\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애프터노트 생성 API 실패 - title blank")
    void createAfternote_BlankTitle_Fail() throws Exception {
        mockMvc.perform(post("/api/afternotes")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SOCIAL\",\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애프터노트 수정 API 성공")
    void updateAfternote_Success() throws Exception {
        given(afternoteService.updateAfternote(eq(USER_ID), eq(3L), any())).willReturn(null);

        mockMvc.perform(patch("/api/afternotes/{afternoteId}", 3L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SOCIAL\",\"title\":\"title\"}"))
                .andExpect(status().isOk());

        verify(afternoteService).updateAfternote(eq(USER_ID), eq(3L), any());
    }

    @Test
    @DisplayName("애프터노트 수정 API 실패 - title 누락")
    void updateAfternote_MissingTitle_Fail() throws Exception {
        mockMvc.perform(patch("/api/afternotes/{afternoteId}", 3L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SOCIAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애프터노트 수정 API 실패 - 잘못된 afternoteId 타입")
    void updateAfternote_InvalidIdType_Fail() throws Exception {
        mockMvc.perform(patch("/api/afternotes/not-number")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SOCIAL\",\"title\":\"title\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("애프터노트 삭제 API 성공")
    void deleteAfternote_Success() throws Exception {
        mockMvc.perform(delete("/api/afternotes/{afternoteId}", 3L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk());

        verify(afternoteService).deleteAfternote(USER_ID, 3L);
    }

    @Test
    @DisplayName("애프터노트 삭제 API 실패 - 잘못된 afternoteId 타입")
    void deleteAfternote_InvalidIdType_Fail() throws Exception {
        mockMvc.perform(delete("/api/afternotes/not-number")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isBadRequest());
    }

        @Test
        @DisplayName("애프터노트 상세 조회 API 실패 - 존재하지 않는 애프터노트")
        void getDetailAfternote_NotFound_Fail() throws Exception {
        given(afternoteService.getDetailAfternote(USER_ID, 999L))
            .willThrow(new CustomException(ErrorCode.AFTERNOTE_NOT_FOUND));

        mockMvc.perform(get("/api/afternotes/{afternoteId}", 999L)
                .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
            .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("애프터노트 상세 조회 API 실패 - 접근 권한 없음")
        void getDetailAfternote_AccessDenied_Fail() throws Exception {
        given(afternoteService.getDetailAfternote(USER_ID, 7L))
            .willThrow(new CustomException(ErrorCode.AFTERNOTE_ACCESS_DENIED));

        mockMvc.perform(get("/api/afternotes/{afternoteId}", 7L)
                .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
            .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("애프터노트 삭제 API 실패 - 인증되지 않은 요청")
        void deleteAfternote_Unauthorized_Fail() throws Exception {
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.UNAUTHORIZED))
            .when(afternoteService).deleteAfternote(USER_ID, 3L);

        mockMvc.perform(delete("/api/afternotes/{afternoteId}", 3L)
                .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
            .andExpect(status().isUnauthorized());
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
