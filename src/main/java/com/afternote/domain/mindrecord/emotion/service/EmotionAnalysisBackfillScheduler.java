package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.event.EmotionAnalysisRunner;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.service.EmotionService.RetryCandidate;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Gemini 일시 실패로 PENDING에 남은 감정 분석과, 감정 행 자체가 없는 누락분을 주기적으로 재시도한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmotionAnalysisBackfillScheduler {

    private final EmotionService emotionService;
    private final EmotionAnalysisRunner emotionAnalysisRunner;
    private final DiaryRepository diaryRepository;
    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final DeepThoughtRepository deepThoughtRepository;

    private Map<EmotionSourceType, Consumer<RetryCandidate>> analysisDispatchers;

    @Value("${afternote.emotion-analysis.backfill-batch-size:20}")
    private int batchSize = 20;

    @PostConstruct
    void initAnalysisDispatchers() {
        this.analysisDispatchers = createAnalysisDispatchers();
    }

    @Scheduled(fixedDelayString = "${afternote.emotion-analysis.backfill-delay-ms:300000}")
    public void backfill() {
        List<RetryCandidate> candidates = new ArrayList<>(emotionService.findRetryCandidates(batchSize));
        int remaining = Math.max(0, batchSize - candidates.size());
        if (remaining > 0) {
            candidates.addAll(findMissingEmotionSources(remaining));
        }
        if (candidates.isEmpty()) {
            return;
        }
        log.info("[EmotionBackfill] retrying {} pending/missing analyses", candidates.size());
        candidates.forEach(this::dispatch);
    }

    private List<RetryCandidate> findMissingEmotionSources(int limit) {
        List<RetryCandidate> missing = new ArrayList<>();
        PageRequest page = PageRequest.of(0, limit);
        for (Object[] row : diaryRepository.findFinalDiariesMissingEmotion(page)) {
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

    private void dispatch(RetryCandidate candidate) {
        Consumer<RetryCandidate> dispatcher = analysisDispatchers.get(candidate.sourceType());
        if (dispatcher == null) {
            throw new IllegalStateException("No emotion analysis dispatcher for sourceType: " + candidate.sourceType());
        }
        dispatcher.accept(candidate);
    }

    private Map<EmotionSourceType, Consumer<RetryCandidate>> createAnalysisDispatchers() {
        Map<EmotionSourceType, Consumer<RetryCandidate>> dispatchers = new EnumMap<>(EmotionSourceType.class);
        dispatchers.put(
                EmotionSourceType.DIARY,
                candidate -> emotionAnalysisRunner.runDiaryAnalysis(candidate.userId(), candidate.sourceId())
        );
        dispatchers.put(
                EmotionSourceType.DAILY_QUESTION,
                candidate -> emotionAnalysisRunner.runDailyQuestionAnalysis(candidate.userId(), candidate.sourceId())
        );
        dispatchers.put(
                EmotionSourceType.DEEP_THOUGHT,
                candidate -> emotionAnalysisRunner.runDeepThoughtAnalysis(candidate.userId(), candidate.sourceId())
        );
        if (!dispatchers.keySet().containsAll(Set.of(EmotionSourceType.values()))) {
            throw new IllegalStateException("Missing emotion analysis dispatcher");
        }
        return Map.copyOf(dispatchers);
    }
}
