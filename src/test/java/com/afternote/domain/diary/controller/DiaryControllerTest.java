package com.afternote.domain.diary.controller;

import com.afternote.domain.diary.dto.DiaryListResponse;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.service.DiaryService;
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
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

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
class DiaryControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private DiaryController diaryController;

    @Mock
    private DiaryService diaryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(diaryController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Diary 작성 API 성공")
    void createDiary_Success() throws Exception {
        given(diaryService.createDiary(eq(USER_ID), any())).willReturn(sampleResponse(10L));

        mockMvc.perform(post("/api/v1/diary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"content\":\"c\",\"isDraft\":false,\"todayMood\":\"HAPPY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.diaryId").value(10))
            .andExpect(jsonPath("$.data.todayMood").value("HAPPY"));

        verify(diaryService).createDiary(eq(USER_ID), any());
    }

    @Test
    @DisplayName("Diary 작성 API 실패 - todayMood 누락")
    void createDiary_MissingTodayMood_Fail() throws Exception {
        mockMvc.perform(post("/api/v1/diary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"isDraft\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Diary 월 단위 조회 API 성공")
    void getDiaries_Success() throws Exception {
        java.time.YearMonth yearMonth = java.time.YearMonth.of(2020, 3);
        given(diaryService.getDiariesByMonth(USER_ID, yearMonth, null))
                .willReturn(DiaryListResponse.from(yearMonth, List.of(sampleResponse(1L)), 1L, TodayMood.HAPPY));

        mockMvc.perform(get("/api/v1/diary").queryParam("yearMonth", "2020-03")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.yearMonth").value("2020-03"))
                .andExpect(jsonPath("$.data.monthDiaryCount").value(1))
                .andExpect(jsonPath("$.data.weeklyDominantMood").value("HAPPY"))
                .andExpect(jsonPath("$.data.diaries[0].diaryId").value(1));

        verify(diaryService).getDiariesByMonth(USER_ID, yearMonth, null);
    }

    @Test
    @DisplayName("Diary 수정 API 성공")
    void updateDiary_Success() throws Exception {
        given(diaryService.updateDiary(eq(USER_ID), eq(10L), any())).willReturn(sampleResponse(10L));

        mockMvc.perform(patch("/api/v1/diary/{diaryId}", 10L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.diaryId").value(10));

        verify(diaryService).updateDiary(eq(USER_ID), eq(10L), any());
    }

    @Test
    @DisplayName("Diary 삭제 API 성공")
    void deleteDiary_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/diary/{diaryId}", 10L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(diaryService).deleteDiary(USER_ID, 10L);
    }

    private DiaryResponse sampleResponse(Long id) {
        return DiaryResponse.builder()
                .diaryId(id)
                .title("title")
                .content("content")
                .isDraft(false)
                .emotion("happy")
            .todayMood(TodayMood.HAPPY)
                .createdAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN)))
                .updatedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN)))
                .build();
    }

    private static class UserIdTestArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(UserId.class)
                    && Long.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            Object userId = webRequest.getAttribute(UserIdArgumentResolver.USER_ID_ATTRIBUTE, NativeWebRequest.SCOPE_REQUEST);
            return userId == null ? USER_ID : userId;
        }
    }
}
