package com.afternote.domain.mindrecord.weekly.service;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisPolicy;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.mindrecord.weekly.model.WeeklyReport;
import com.afternote.domain.mindrecord.weekly.repository.WeeklyReportRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.service.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WeeklyMindRecordServiceSummaryTest {

    @InjectMocks
    private WeeklyMindRecordService weeklyMindRecordService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private UserDailyQuestionRepository userDailyQuestionRepository;
    @Mock
    private DeepThoughtRepository deepThoughtRepository;
    @Mock
    private EmotionRepository emotionRepository;
    @Mock
    private WeeklyReportRepository weeklyReportRepository;
    @Mock
    private GeminiService geminiService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private EmotionAnalysisPolicy emotionAnalysisPolicy;

    @Test
    @DisplayName("keywordJson 동일·유효 요약이 있으면 Gemini를 호출하지 않는다")
    void cacheHit_skipsGemini() throws Exception {
        User user = sampleUser(1L);
        LocalDate date = LocalDate.of(2026, 8, 3); // Monday
        LocalDateTime weekStart = date.atStartOfDay();

        WeeklyReport cached = WeeklyReport.create(
                user,
                weekStart,
                date.plusDays(6).atTime(23, 59, 59),
                "캐시된 주간 요약입니다.",
                "[]"
        );

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(diaryRepository
                .findByUserIdAndIsDraftFalseAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateAscCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(userDailyQuestionRepository
                .findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(any(), any(), any()))
                .willReturn(List.of());
        given(deepThoughtRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");
        given(weeklyReportRepository.findByUserIdAndStartDate(1L, weekStart)).willReturn(Optional.of(cached));
        given(emotionAnalysisPolicy.isWeekClosed(1L, date)).willReturn(false);

        var response = weeklyMindRecordService.getWeeklyMindRecord(1L, date);

        assertThat(response.summaryText()).isEqualTo("캐시된 주간 요약입니다.");
        verify(geminiService, never()).generateWeeklyMindRecordSummary(anyString());
    }

    @Test
    @DisplayName("MySQL json 공백 정규화와 Jackson compact 직렬화가 달라도 동일로 본다")
    void sameKeywordJson_ignoresMysqlSpacing() {
        ReflectionTestUtils.setField(weeklyMindRecordService, "objectMapper", new ObjectMapper());

        Boolean same = ReflectionTestUtils.invokeMethod(
                weeklyMindRecordService,
                "sameKeywordJson",
                "[{\"keyword\": \"평온\", \"percentage\": 100}]",
                "[{\"keyword\":\"평온\",\"percentage\":100}]"
        );

        assertThat(same).isTrue();
    }

    @Test
    @DisplayName("닫힌 주는 저장된 요약·키워드를 쓰고 Gemini를 호출하지 않는다")
    void closedWeek_usesFrozenSummary() throws Exception {
        User user = sampleUser(1L);
        LocalDate date = LocalDate.of(2026, 8, 3);
        LocalDateTime weekStart = date.atStartOfDay();
        String storedJson = "[{\"keyword\":\"감사\",\"percentage\":100}]";
        WeeklyReport cached = WeeklyReport.create(
                user, weekStart, date.plusDays(6).atTime(23, 59, 59), "고정된 요약", storedJson);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(diaryRepository
                .findByUserIdAndIsDraftFalseAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateAscCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(userDailyQuestionRepository
                .findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(any(), any(), any()))
                .willReturn(List.of());
        given(deepThoughtRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(weeklyReportRepository.findByUserIdAndStartDate(1L, weekStart)).willReturn(Optional.of(cached));
        given(emotionAnalysisPolicy.isWeekClosed(1L, date)).willReturn(true);
        ReflectionTestUtils.setField(weeklyMindRecordService, "objectMapper", new ObjectMapper());

        var response = weeklyMindRecordService.getWeeklyMindRecord(1L, date);

        assertThat(response.summaryText()).isEqualTo("고정된 요약");
        assertThat(response.emotions()).hasSize(1);
        assertThat(response.emotions().get(0).keyword()).isEqualTo("감사");
        verify(geminiService, never()).generateWeeklyMindRecordSummary(anyString());
    }

    @Test
    @DisplayName("스케줄러는 직전 주 기록이 있는 사용자에게 리포트를 저장한다")
    void generateReportsForWeek_persists() throws Exception {
        User user = sampleUser(1L);
        LocalDate weekMonday = LocalDate.of(2026, 8, 3);
        given(diaryRepository.findUserIdsWithFinalDiariesInEntryDateRange(weekMonday, weekMonday.plusWeeks(1)))
                .willReturn(List.of(1L));
        given(userDailyQuestionRepository.findUserIdsWithFinalAnswersInQuestionDateRange(
                weekMonday, weekMonday.plusDays(6)))
                .willReturn(List.of());
        given(deepThoughtRepository.findUserIdsWithFinalDeepThoughtsInCreatedAtRange(any(), any()))
                .willReturn(List.of());
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(diaryRepository
                .findByUserIdAndIsDraftFalseAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateAscCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(userDailyQuestionRepository
                .findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(any(), any(), any()))
                .willReturn(List.of());
        given(deepThoughtRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(objectMapper.writeValueAsString(any())).willReturn("[]");

        int created = weeklyMindRecordService.generateReportsForWeek(weekMonday);

        assertThat(created).isEqualTo(1);
        verify(weeklyReportRepository).save(any(WeeklyReport.class));
        verify(geminiService, never()).generateWeeklyMindRecordSummary(anyString());
    }

    private static User sampleUser(Long id) {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
