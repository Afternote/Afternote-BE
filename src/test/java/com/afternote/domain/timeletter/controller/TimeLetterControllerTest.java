package com.afternote.domain.timeletter.controller;

import com.afternote.domain.timeletter.dto.TimeLetterListResponse;
import com.afternote.domain.timeletter.dto.TimeLetterResponse;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.timeletter.service.TimeLetterService;
import com.afternote.global.resolver.UserId;
import com.afternote.global.resolver.UserIdArgumentResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TimeLetterControllerTest {

    private static final long USER_ID = 1L;

    @InjectMocks
    private TimeLetterController timeLetterController;

    @Mock
    private TimeLetterService timeLetterService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.mockMvc = MockMvcBuilders.standaloneSetup(timeLetterController)
                .setCustomArgumentResolvers(new UserIdTestArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("타임레터 전체 조회 API 성공")
    void getTimeLetters_Success() throws Exception {
        given(timeLetterService.getTimeLetters(USER_ID))
                .willReturn(TimeLetterListResponse.from(List.of(sampleResponse(1L, "title"))));

        mockMvc.perform(get("/api/v1/time-letters")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value(200));

        verify(timeLetterService).getTimeLetters(USER_ID);
    }

    @Test
    @DisplayName("타임레터 단일 조회 API 성공")
    void getTimeLetter_Success() throws Exception {
        given(timeLetterService.getTimeLetter(USER_ID, 10L))
                .willReturn(sampleResponse(10L, "one"));

        mockMvc.perform(get("/api/v1/time-letters/{timeLetterId}", 10L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(10));

        verify(timeLetterService).getTimeLetter(USER_ID, 10L);
    }

    @Test
    @DisplayName("타임레터 생성 API 성공")
    void createTimeLetter_Success() throws Exception {
        given(timeLetterService.createTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(sampleResponse(20L, "created"));

        String requestBody = """
                {
                  "title": "임시 제목",
                  "status": "DRAFT",
                  "receiverIds": [1]
                }
                """;

        mockMvc.perform(post("/api/v1/time-letters")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(20));

        verify(timeLetterService).createTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any());
    }

                @Test
                @DisplayName("타임레터 생성 API 실패 - status 누락")
                void createTimeLetter_MissingStatus_Fail() throws Exception {
                                String requestBody = """
                                                                {
                                                                        "title": "임시 제목"
                                                                }
                                                                """;

                                mockMvc.perform(post("/api/v1/time-letters")
                                                                                                .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content(requestBody))
                                                                .andExpect(status().isBadRequest());
                }

    @Test
    @DisplayName("임시저장 목록 조회 API 성공")
    void getTemporaryTimeLetters_Success() throws Exception {
        given(timeLetterService.getTemporaryTimeLetters(USER_ID))
                .willReturn(TimeLetterListResponse.from(List.of(sampleResponse(30L, "draft"))));

        mockMvc.perform(get("/api/v1/time-letters/temporary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(timeLetterService).getTemporaryTimeLetters(USER_ID);
    }

    @Test
    @DisplayName("타임레터 다건 삭제 API 성공")
    void deleteTimeLetters_Success() throws Exception {
        String requestBody = objectMapper.writeValueAsString(java.util.Map.of("timeLetterIds", List.of(1L, 2L)));

        mockMvc.perform(post("/api/v1/time-letters/delete")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(timeLetterService).deleteTimeLetters(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("임시저장 전체 삭제 API 성공")
    void deleteAllTemporary_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/time-letters/temporary")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));

        verify(timeLetterService).deleteAllTemporary(USER_ID);
    }

    @Test
    @DisplayName("타임레터 수정 API 성공")
    void updateTimeLetter_Success() throws Exception {
        given(timeLetterService.updateTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any()))
                .willReturn(sampleResponse(10L, "updated"));

        String requestBody = """
                {
                  "title": "수정 제목",
                  "status": "DRAFT"
                }
                """;

        mockMvc.perform(patch("/api/v1/time-letters/{timeLetterId}", 10L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(10));

        verify(timeLetterService).updateTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.any());
    }

    private TimeLetterResponse sampleResponse(Long id, String title) {
        return TimeLetterResponse.builder()
                .id(id)
                .title(title)
                .content("content")
                .status(TimeLetterStatus.DRAFT)
                .sendAt(LocalDateTime.now().plusDays(1))
                .receiverIds(List.of(1L))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
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
