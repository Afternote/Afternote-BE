package com.afternote.domain.diary.controller;

import com.afternote.domain.diary.dto.DiaryListResponse;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.service.DiaryService;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.exception.GlobalExceptionHandler;
import com.afternote.global.resolver.UserId;
import com.afternote.global.resolver.UserIdArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(diaryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    @DisplayName("Diary 작성 API 성공")
    void createDiary_Success() throws Exception {
        given(diaryService.createDiary(eq(USER_ID), any())).willReturn(sampleResponse(10L));

        mockMvc.perform(post("/api/v1/diary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"t\",\"content\":\"c\",\"isDraft\":false,\"todayMood\":\"HAPPY\",\"date\":\"2026-08-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.diaryId").value(10))
            .andExpect(jsonPath("$.data.todayMood").value("HAPPY"));

        verify(diaryService).createDiary(eq(USER_ID), any());
    }

    @Test
    @DisplayName("Diary 작성 API 실패 - todayMood 누락은 400/1400")
    void createDiary_MissingTodayMood_Fail() throws Exception {
        mockMvc.perform(post("/api/v1/diary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"isDraft\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(1400));
    }

    @Test
    @DisplayName("Diary 작성 API 실패 - 날짜 형식이 아니면 400/1400")
    void createDiary_InvalidDateFormat_Fail() throws Exception {
        mockMvc.perform(post("/api/v1/diary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"isDraft\":false,\"todayMood\":\"HAPPY\",\"date\":\"2026/08/01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(1400));
    }

    @Test
    @DisplayName("Diary 작성 API 실패 - 미래 기록일은 400/2101")
    void createDiary_FutureDate_Fail() throws Exception {
        given(diaryService.createDiary(eq(USER_ID), any()))
                .willThrow(new CustomException(ErrorCode.DIARY_INVALID_DATE));

        mockMvc.perform(post("/api/v1/diary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"content\":\"c\",\"isDraft\":false,\"todayMood\":\"HAPPY\",\"date\":\"2099-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(2101));
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
                        .content("{\"title\":\"updated\",\"date\":\"2026-08-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.diaryId").value(10));

        verify(diaryService).updateDiary(eq(USER_ID), eq(10L), any());
    }

    @Test
    @DisplayName("Diary 수정 API 실패 - 미래 기록일은 400/2101")
    void updateDiary_FutureDate_Fail() throws Exception {
        given(diaryService.updateDiary(eq(USER_ID), eq(10L), any()))
                .willThrow(new CustomException(ErrorCode.DIARY_INVALID_DATE));

        mockMvc.perform(patch("/api/v1/diary/{diaryId}", 10L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2099-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value(2101));
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
                .date(java.time.LocalDate.of(2026, 8, 1))
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
