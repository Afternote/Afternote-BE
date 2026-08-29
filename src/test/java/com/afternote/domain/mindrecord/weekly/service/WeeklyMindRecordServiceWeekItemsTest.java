package com.afternote.domain.mindrecord.weekly.service;

import com.afternote.domain.dailyquestion.model.DailyQuestion;
import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisPolicy;
import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.mindrecord.weekly.dto.WeekRecordType;
import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse.WeekRecordItem;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class WeeklyMindRecordServiceWeekItemsTest {

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
    @DisplayName("week[].emotion 은 todayMood만 쓰고 Gemini 분석 카테고리는 넣지 않는다")
    void weekEmotion_usesTodayMoodOnly_notAnalyzedCategory() throws Exception {
        stubCommon();
        User user = sampleUser(1L);
        LocalDate monday = LocalDate.of(2026, 8, 3);

        Diary diary = diary(user, 7L, monday.plusDays(1).atTime(10, 0), TodayMood.HAPPY);
        UserDailyQuestion dq = dailyQuestion(user, 22L, monday.plusDays(2), monday.plusDays(2).atTime(11, 0));

        Emotion analyzed = Emotion.createSucceeded(
                user, EmotionSourceType.DIARY, 7L, "기쁨", LocalDateTime.now());

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(diaryRepository
                .findByUserIdAndIsDraftFalseAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateAscCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of(diary));
        given(userDailyQuestionRepository
                .findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(any(), any(), any()))
                .willReturn(List.of(dq));
        given(deepThoughtRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(
                eq(1L), eq(EmotionSourceType.DIARY), anyList()))
                .willReturn(List.of(analyzed));
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(
                eq(1L), eq(EmotionSourceType.DAILY_QUESTION), anyList()))
                .willReturn(List.of());
        given(weeklyReportRepository.findByUserIdAndStartDate(any(), any())).willReturn(Optional.empty());
        given(geminiService.generateWeeklyMindRecordSummary(any())).willReturn("요약");

        var response = weeklyMindRecordService.getWeeklyMindRecord(1L, monday);

        assertThat(response.week()).hasSize(2);

        WeekRecordItem diaryItem = response.week().stream()
                .filter(item -> item.type() == WeekRecordType.DIARY)
                .findFirst()
                .orElseThrow();
        assertThat(diaryItem.emotion()).isEqualTo("HAPPY");
        assertThat(diaryItem.emotion()).isNotEqualTo("기쁨");

        WeekRecordItem dqItem = response.week().stream()
                .filter(item -> item.type() == WeekRecordType.DAILY_QUESTION)
                .findFirst()
                .orElseThrow();
        assertThat(dqItem.emotion()).isNull();

        assertThat(response.emotions())
                .extracting(e -> e.keyword())
                .contains("기쁨");
    }

    @Test
    @DisplayName("같은 날 일기+DQ면 day당 1개(일기 우선), 일기 여러 개면 최신 todayMood")
    void week_onePerDay_diaryPreferred_latestMood() {
        stubCommon();
        User user = sampleUser(1L);
        LocalDate monday = LocalDate.of(2026, 8, 3);
        LocalDate day = monday.plusDays(1);

        Diary older = diary(user, 1L, day.atTime(9, 0), TodayMood.SAD);
        Diary newer = diary(user, 2L, day.atTime(18, 0), TodayMood.HAPPY);
        UserDailyQuestion dq = dailyQuestion(user, 30L, day, day.atTime(12, 0));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(diaryRepository
                .findByUserIdAndIsDraftFalseAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateAscCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of(older, newer));
        given(userDailyQuestionRepository
                .findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(any(), any(), any()))
                .willReturn(List.of(dq));
        given(deepThoughtRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(any(), any(), anyList()))
                .willReturn(List.of());
        given(weeklyReportRepository.findByUserIdAndStartDate(any(), any())).willReturn(Optional.empty());

        var response = weeklyMindRecordService.getWeeklyMindRecord(1L, monday);

        assertThat(response.week()).hasSize(1);
        WeekRecordItem item = response.week().get(0);
        assertThat(item.day()).isEqualTo(day.getDayOfMonth());
        assertThat(item.type()).isEqualTo(WeekRecordType.DIARY);
        assertThat(item.diaryId()).isEqualTo(2L);
        assertThat(item.emotion()).isEqualTo("HAPPY");
    }

    @Test
    @DisplayName("week[] 일기는 createdAt이 아니라 기록일(entryDate)로 그루핑한다")
    void week_groupsDiaryByEntryDate_notCreatedAt() {
        stubCommon();
        User user = sampleUser(1L);
        LocalDate monday = LocalDate.of(2026, 8, 3);
        LocalDate entryDate = monday.plusDays(2);
        Diary diary = Diary.create(user, "제목", "본문", false, TodayMood.SAD, entryDate);
        ReflectionTestUtils.setField(diary, "id", 9L);
        ReflectionTestUtils.setField(diary, "createdAt", monday.atTime(10, 0));
        ReflectionTestUtils.setField(diary, "updatedAt", monday.atTime(10, 0));

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(diaryRepository
                .findByUserIdAndIsDraftFalseAndEntryDateGreaterThanEqualAndEntryDateLessThanOrderByEntryDateAscCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of(diary));
        given(userDailyQuestionRepository
                .findByUserIdAndQuestionDateBetweenOrderByQuestionDateAscCreatedAtAsc(any(), any(), any()))
                .willReturn(List.of());
        given(deepThoughtRepository
                .findByUserIdAndIsDraftFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(
                        any(), any(), any()))
                .willReturn(List.of());
        given(emotionRepository.findByUserIdAndSourceTypeAndSourceIdIn(any(), any(), anyList()))
                .willReturn(List.of());
        given(weeklyReportRepository.findByUserIdAndStartDate(any(), any())).willReturn(Optional.empty());

        var response = weeklyMindRecordService.getWeeklyMindRecord(1L, monday);

        assertThat(response.week()).hasSize(1);
        assertThat(response.week().get(0).day()).isEqualTo(entryDate.getDayOfMonth());
        assertThat(response.week().get(0).type()).isEqualTo(WeekRecordType.DIARY);
    }

    private void stubCommon() {
        ReflectionTestUtils.setField(weeklyMindRecordService, "objectMapper", new ObjectMapper());
    }

    private static Diary diary(User user, Long id, LocalDateTime at, TodayMood mood) {
        Diary diary = Diary.create(user, "제목", "본문", false, mood, at.toLocalDate());
        ReflectionTestUtils.setField(diary, "id", id);
        ReflectionTestUtils.setField(diary, "createdAt", at);
        ReflectionTestUtils.setField(diary, "updatedAt", at);
        return diary;
    }

    private static UserDailyQuestion dailyQuestion(User user, Long id, LocalDate questionDate, LocalDateTime at) {
        DailyQuestion question = new DailyQuestion();
        ReflectionTestUtils.setField(question, "id", 1L);
        ReflectionTestUtils.setField(question, "content", "질문");

        UserDailyQuestion dq = UserDailyQuestion.builder()
                .user(user)
                .dailyQuestion(question)
                .questionDate(questionDate)
                .isAnswered(true)
                .content("답변")
                .isDraft(false)
                .build();
        ReflectionTestUtils.setField(dq, "id", id);
        ReflectionTestUtils.setField(dq, "createdAt", at);
        return dq;
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
