package com.afternote.domain.mindrecord.emotion.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmotionAnalysisEventListener {

    private final EmotionAnalysisRunner runner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiary(DiaryEmotionAnalysisRequestedEvent event) {
        runner.runDiaryAnalysis(event.userId(), event.diaryId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDailyQuestion(DailyQuestionEmotionAnalysisRequestedEvent event) {
        runner.runDailyQuestionAnalysis(event.userId(), event.userDailyQuestionId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeepThought(DeepThoughtEmotionAnalysisRequestedEvent event) {
        runner.runDeepThoughtAnalysis(event.userId(), event.deepThoughtId());
    }
}
