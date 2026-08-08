package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.mindrecord.emotion.event.EmotionAnalysisRunner;
import com.afternote.domain.mindrecord.emotion.service.EmotionService.RetryCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gemini 일시 실패로 PENDING에 남은 감정 분석을 주기적으로 재시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisBackfillScheduler {

    private final EmotionService emotionService;
    private final EmotionAnalysisRunner emotionAnalysisRunner;

    @Value("${afternote.emotion-analysis.backfill-batch-size:20}")
    private int batchSize = 20;

    @Scheduled(fixedDelayString = "${afternote.emotion-analysis.backfill-delay-ms:300000}")
    public void backfill() {
        List<RetryCandidate> candidates = emotionService.findRetryCandidates(batchSize);
        if (candidates.isEmpty()) {
            return;
        }
        log.info("[EmotionBackfill] retrying {} pending analyses", candidates.size());
        for (RetryCandidate candidate : candidates) {
            dispatch(candidate);
        }
    }

    private void dispatch(RetryCandidate candidate) {
        switch (candidate.sourceType()) {
            case DIARY -> emotionAnalysisRunner.runDiaryAnalysis(candidate.userId(), candidate.sourceId());
            case DAILY_QUESTION -> emotionAnalysisRunner.runDailyQuestionAnalysis(
                    candidate.userId(), candidate.sourceId());
            case DEEP_THOUGHT -> emotionAnalysisRunner.runDeepThoughtAnalysis(
                    candidate.userId(), candidate.sourceId());
        }
    }
}
