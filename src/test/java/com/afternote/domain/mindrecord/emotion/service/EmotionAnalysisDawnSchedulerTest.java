package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisPolicy;
import com.afternote.domain.mindrecord.emotion.event.EmotionAnalysisRunner;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.service.EmotionService.RetryCandidate;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisDawnSchedulerTest {

    @Mock
    private EmotionService emotionService;
    @Mock
    private EmotionAnalysisPolicy emotionAnalysisPolicy;
    @Mock
    private EmotionAnalysisRunner emotionAnalysisRunner;
    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private UserDailyQuestionRepository userDailyQuestionRepository;
    @Mock
    private DeepThoughtRepository deepThoughtRepository;

    @InjectMocks
    private EmotionAnalysisDawnScheduler scheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "dawnBatchSize", 100);
        given(emotionService.findPendingForDawn(anyInt())).willReturn(List.of());
        given(diaryRepository.findFinalDiariesMissingEmotion(any(Pageable.class))).willReturn(List.of());
        given(userDailyQuestionRepository.findFinalAnswersMissingEmotion(any(Pageable.class))).willReturn(List.of());
        given(deepThoughtRepository.findFinalDeepThoughtsMissingEmotion(any(Pageable.class))).willReturn(List.of());
    }

    @Test
    @DisplayName("열린 주 FAILED는 PENDING으로 되돌리고 러너에 넣는다")
    void openWeekFailed_revivedAndDispatched() {
        LocalDate entryDate = LocalDate.of(2026, 8, 10);
        Diary diary = Diary.create(sampleUser(), "t", "c", false, TodayMood.HAPPY, entryDate);
        RetryCandidate candidate = new RetryCandidate(1L, EmotionSourceType.DIARY, 10L);
        given(emotionService.findFailedForDawn(100)).willReturn(List.of(candidate));
        given(diaryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(diary));
        given(emotionAnalysisPolicy.isWeekClosed(1L, entryDate)).willReturn(false);
        given(emotionService.reviveFailed(1L, EmotionSourceType.DIARY, 10L)).willReturn(true);

        scheduler.dawnSweep();

        verify(emotionService).reviveFailed(1L, EmotionSourceType.DIARY, 10L);
        verify(emotionAnalysisRunner).runDiaryAnalysis(1L, 10L);
    }

    @Test
    @DisplayName("닫힌 주 FAILED는 리셋하지 않고 Gemini도 호출하지 않는다")
    void closedWeekFailed_skipped() {
        LocalDate entryDate = LocalDate.of(2026, 8, 3);
        Diary diary = Diary.create(sampleUser(), "t", "c", false, TodayMood.HAPPY, entryDate);
        RetryCandidate candidate = new RetryCandidate(1L, EmotionSourceType.DIARY, 10L);
        given(emotionService.findFailedForDawn(100)).willReturn(List.of(candidate));
        given(diaryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(diary));
        given(emotionAnalysisPolicy.isWeekClosed(1L, entryDate)).willReturn(true);

        scheduler.dawnSweep();

        verify(emotionService, never()).reviveFailed(any(), any(), any());
        verify(emotionAnalysisRunner, never()).runDiaryAnalysis(any(), any());
    }

    private static User sampleUser() {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
