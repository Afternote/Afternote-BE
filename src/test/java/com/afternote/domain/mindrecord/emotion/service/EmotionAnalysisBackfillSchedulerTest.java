package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.event.EmotionAnalysisRunner;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.service.EmotionService.RetryCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmotionAnalysisBackfillSchedulerTest {

    @InjectMocks
    private EmotionAnalysisBackfillScheduler scheduler;

    @Mock
    private EmotionService emotionService;
    @Mock
    private EmotionAnalysisRunner emotionAnalysisRunner;
    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private UserDailyQuestionRepository userDailyQuestionRepository;
    @Mock
    private DeepThoughtRepository deepThoughtRepository;

    @BeforeEach
    void setUp() {
        scheduler.initAnalysisDispatchers();
    }

    @Test
    @DisplayName("DIARY 재시도 후보는 diary 분석 runner로 전달된다")
    void backfill_DispatchesDiaryCandidate() {
        given(emotionService.findRetryCandidates(20))
                .willReturn(List.of(new RetryCandidate(1L, EmotionSourceType.DIARY, 10L)));

        scheduler.backfill();

        verify(emotionAnalysisRunner).runDiaryAnalysis(1L, 10L);
    }

    @Test
    @DisplayName("DAILY_QUESTION 재시도 후보는 daily question 분석 runner로 전달된다")
    void backfill_DispatchesDailyQuestionCandidate() {
        given(emotionService.findRetryCandidates(20))
                .willReturn(List.of(new RetryCandidate(1L, EmotionSourceType.DAILY_QUESTION, 11L)));

        scheduler.backfill();

        verify(emotionAnalysisRunner).runDailyQuestionAnalysis(1L, 11L);
    }

    @Test
    @DisplayName("DEEP_THOUGHT 재시도 후보는 deep thought 분석 runner로 전달된다")
    void backfill_DispatchesDeepThoughtCandidate() {
        given(emotionService.findRetryCandidates(20))
                .willReturn(List.of(new RetryCandidate(1L, EmotionSourceType.DEEP_THOUGHT, 12L)));

        scheduler.backfill();

        verify(emotionAnalysisRunner).runDeepThoughtAnalysis(1L, 12L);
    }
}
