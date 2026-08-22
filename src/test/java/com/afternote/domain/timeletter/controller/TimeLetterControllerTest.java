package com.afternote.domain.timeletter.controller;

import com.afternote.domain.timeletter.dto.request.TimeLetterCreateRequest;
import com.afternote.domain.timeletter.dto.request.TimeLetterUpdateRequest;
import com.afternote.domain.timeletter.dto.response.TimeLetterListResponse;
import com.afternote.domain.timeletter.dto.response.TimeLetterResponse;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.deliveredAt").doesNotExist());

        verify(timeLetterService).getTimeLetter(USER_ID, 10L);
    }

    @Test
    @DisplayName("DRAFT 생성 API는 수신자 목록을 생략할 수 있다")
    void createTimeLetter_DraftWithoutReceivers_Success() throws Exception {
        given(timeLetterService.createTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(sampleResponse(20L, "created"));

        String requestBody = """
                {
                  "status": "DRAFT"
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
    @DisplayName("DRAFT 생성 API는 빈 수신자 목록을 허용한다")
    void createTimeLetter_DraftWithEmptyReceivers_Success() throws Exception {
        given(timeLetterService.createTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(sampleResponse(21L, "created"));

        String requestBody = """
                {
                  "status": "DRAFT",
                  "receiverIds": []
                }
                """;

        mockMvc.perform(post("/api/v1/time-letters")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(21));

        verify(timeLetterService).createTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest(name = "{0} sendAt={1}")
    @CsvSource({
            "DRAFT,     2026-09-03T10:13:48Z",
            "DRAFT,     2026-09-03T10:13:48+00:00",
            "DRAFT,     2026-09-03T10:13:48.123Z",
            "SCHEDULED, 2026-09-03T10:13:48Z",
            "SCHEDULED, 2026-09-03T10:13:48+00:00"
    })
    @DisplayName("생성 API는 오프셋이 포함된 sendAt을 OffsetDateTime으로 역직렬화한다")
    void createTimeLetter_DeserializesOffsetSendAt(
            String statusValue,
            String sendAtValue
    ) throws Exception {
        given(timeLetterService.createTimeLetter(org.mockito.ArgumentMatchers.eq(USER_ID), org.mockito.ArgumentMatchers.any()))
                .willReturn(sampleResponse(22L, "created"));

        String requestBody = """
                {
                  "status": "%s",
                  "sendAt": "%s"
                }
                """.formatted(statusValue, sendAtValue);

        mockMvc.perform(post("/api/v1/time-letters")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<TimeLetterCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(TimeLetterCreateRequest.class);
        verify(timeLetterService).createTimeLetter(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                requestCaptor.capture()
        );
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().getSendAt())
                .isEqualTo(OffsetDateTime.parse(sendAtValue));
    }

    @ParameterizedTest(name = "invalid sendAt={0}")
    @ValueSource(strings = {
            "2026-09-03T19:13:48",
            "2026-09-03 10:13:48+00:00"
    })
    @DisplayName("생성 API는 OffsetDateTime으로 해석할 수 없는 sendAt을 400으로 거부한다")
    void createTimeLetter_InvalidSendAt_Fail(String sendAtValue) throws Exception {
        String requestBody = """
                {
                  "status": "DRAFT",
                  "sendAt": "%s"
                }
                """.formatted(sendAtValue);

        mockMvc.perform(post("/api/v1/time-letters")
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(timeLetterService);
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

        mockMvc.perform(delete("/api/v1/time-letters")
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

    @ParameterizedTest(name = "PATCH sendAt={0}")
    @CsvSource({
            "2026-09-03T10:13:48Z",
            "2026-09-03T10:13:48+00:00"
    })
    @DisplayName("수정 API도 생성 API와 동일한 sendAt 입력 계약을 사용한다")
    void updateTimeLetter_DeserializesRfc3339SendAt(String sendAtValue) throws Exception {
        given(timeLetterService.updateTimeLetter(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(sampleResponse(10L, "updated"));

        String requestBody = """
                {
                  "status": "DRAFT",
                  "sendAt": "%s"
                }
                """.formatted(sendAtValue);

        mockMvc.perform(patch("/api/v1/time-letters/{timeLetterId}", 10L)
                        .requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        ArgumentCaptor<TimeLetterUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(TimeLetterUpdateRequest.class);
        verify(timeLetterService).updateTimeLetter(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(10L),
                requestCaptor.capture()
        );
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().getSendAt())
                .isEqualTo(OffsetDateTime.parse(sendAtValue));
    }

    private TimeLetterResponse sampleResponse(Long id, String title) {
        return TimeLetterResponse.builder()
                .id(id)
                .title(title)
                .status(TimeLetterStatus.DRAFT.name())
                .sendAt(LocalDateTime.now().plusDays(1))
                .receiverIds(List.of(1L))
                .blocks(List.of())
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
