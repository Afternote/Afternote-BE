package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.EmotionAnalysisPolicy;
import com.afternote.domain.mindrecord.emotion.event.EmotionAnalysisRunner;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.service.EmotionService.RetryCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 매일 01:00(Asia/Seoul)에 당일 상한을 소진한 FAILED를 열린 주만 PENDING으로 되돌리고,
 * 남은 PENDING·감정 행 누락분도 한 번 더 태운다. 월요일에는 02:00 주간 리포트 직전 마지막 기회다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisDawnScheduler {

    private final EmotionService emotionService;
    private final EmotionAnalysisPolicy emotionAnalysisPolicy;
    private final EmotionAnalysisRunner emotionAnalysisRunner;
    private final DiaryRepository diaryRepository;
    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final DeepThoughtRepository deepThoughtRepository;

    @Value("${afternote.emotion-analysis.dawn-batch-size:100}")
    private int dawnBatchSize = 100;

    @Scheduled(
            cron = "${afternote.emotion-analysis.dawn-cron:0 0 1 * * *}",
            zone = "${afternote.emotion-analysis.dawn-zone:Asia/Seoul}"
    )
    public void dawnSweep() {
        int budget = Math.max(1, dawnBatchSize);
        Set<String> dispatchedKeys = new LinkedHashSet<>();
        int revived = 0;
        int skippedClosed = 0;

        for (RetryCandidate candidate : emotionService.findFailedForDawn(budget)) {
            if (dispatchedKeys.size() >= budget) {
                break;
            }
            LocalDate recordDate = resolveRecordDate(candidate);
            if (recordDate != null && emotionAnalysisPolicy.isWeekClosed(candidate.userId(), recordDate)) {
                skippedClosed++;
                log.info("[EmotionDawn] skip closed-week FAILED userId={} sourceType={} sourceId={} date={}",
                        candidate.userId(), candidate.sourceType(), candidate.sourceId(), recordDate);
                continue;
            }
            if (!emotionService.reviveFailed(candidate.userId(), candidate.sourceType(), candidate.sourceId())) {
                continue;
            }
            revived++;
            dispatch(candidate, dispatchedKeys);
        }

        int remaining = budget - dispatchedKeys.size();
        if (remaining > 0) {
            for (RetryCandidate candidate : emotionService.findPendingForDawn(remaining)) {
                dispatch(candidate, dispatchedKeys);
            }
        }
        remaining = budget - dispatchedKeys.size();
        if (remaining > 0) {
            for (RetryCandidate candidate : findMissingEmotionSources(remaining)) {
                dispatch(candidate, dispatchedKeys);
            }
        }

        if (revived > 0 || skippedClosed > 0 || !dispatchedKeys.isEmpty()) {
            log.info("[EmotionDawn] revived={} skippedClosed={} dispatched={}",
                    revived, skippedClosed, dispatchedKeys.size());
        }
    }

    private void dispatch(RetryCandidate candidate, Set<String> dispatchedKeys) {
        String key = candidate.sourceType() + ":" + candidate.userId() + ":" + candidate.sourceId();
        if (!dispatchedKeys.add(key)) {
            return;
        }
        switch (candidate.sourceType()) {
            case DIARY -> emotionAnalysisRunner.runDiaryAnalysis(candidate.userId(), candidate.sourceId());
            case DAILY_QUESTION -> emotionAnalysisRunner.runDailyQuestionAnalysis(
                    candidate.userId(), candidate.sourceId());
            case DEEP_THOUGHT -> emotionAnalysisRunner.runDeepThoughtAnalysis(
                    candidate.userId(), candidate.sourceId());
        }
    }

    private LocalDate resolveRecordDate(RetryCandidate candidate) {
        return switch (candidate.sourceType()) {
            case DIARY -> diaryRepository.findByIdAndUserId(candidate.sourceId(), candidate.userId())
                    .map(d -> d.getEntryDate() != null
                            ? d.getEntryDate()
                            : (d.getCreatedAt() != null ? d.getCreatedAt().toLocalDate() : null))
                    .orElse(null);
            case DAILY_QUESTION -> userDailyQuestionRepository.findByIdAndUser_Id(
                            candidate.sourceId(), candidate.userId())
                    .map(uq -> uq.getQuestionDate())
                    .orElse(null);
            case DEEP_THOUGHT -> deepThoughtRepository.findByIdAndUserId(candidate.sourceId(), candidate.userId())
                    .map(dt -> dt.getCreatedAt() != null ? dt.getCreatedAt().toLocalDate() : null)
                    .orElse(null);
        };
    }

    private List<RetryCandidate> findMissingEmotionSources(int limit) {
        List<RetryCandidate> missing = new ArrayList<>();
        for (Object[] row : diaryRepository.findFinalDiariesMissingEmotion(PageRequest.of(0, limit))) {
            missing.add(new RetryCandidate(toLong(row[0]), EmotionSourceType.DIARY, toLong(row[1])));
            if (missing.size() >= limit) {
                return missing;
            }
        }
        int left = limit - missing.size();
        if (left > 0) {
            for (Object[] row : userDailyQuestionRepository.findFinalAnswersMissingEmotion(PageRequest.of(0, left))) {
                missing.add(new RetryCandidate(toLong(row[0]), EmotionSourceType.DAILY_QUESTION, toLong(row[1])));
                if (missing.size() >= limit) {
                    return missing;
                }
            }
        }
        left = limit - missing.size();
        if (left > 0) {
            for (Object[] row : deepThoughtRepository.findFinalDeepThoughtsMissingEmotion(PageRequest.of(0, left))) {
                missing.add(new RetryCandidate(toLong(row[0]), EmotionSourceType.DEEP_THOUGHT, toLong(row[1])));
                if (missing.size() >= limit) {
                    return missing;
                }
            }
        }
        return missing;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
