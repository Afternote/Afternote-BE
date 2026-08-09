package com.afternote.domain.mindrecord.weekly.service;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.repository.DiaryRepository;
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
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
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
