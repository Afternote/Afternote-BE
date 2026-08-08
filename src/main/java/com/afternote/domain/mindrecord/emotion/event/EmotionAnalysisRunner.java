package com.afternote.domain.mindrecord.emotion.event;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.mindrecord.emotion.EmotionCategoryAllowlist;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.service.EmotionService;
import com.afternote.global.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnalysisRunner {

    private static final int IMMEDIATE_ATTEMPTS = 2;
    private static final long[] IMMEDIATE_BACKOFF_MS = {0L, 1_000L};

    /** 트랜잭션 종료 후에도 사용할 수 있도록 연관 엔티티를 모두 풀어 문자열만 담는다. */
    private record DailyQuestionEmotionSnapshot(
            String questionContent,
            String answerContent,
            LocalDateTime createdAt
    ) {}

    private final TransactionTemplate transactionTemplate;
    private final DiaryRepository diaryRepository;
    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final DeepThoughtRepository deepThoughtRepository;
    private final GeminiService geminiService;
    private final EmotionService emotionService;

    @Async
    public void runDiaryAnalysis(Long userId, Long diaryId) {
        Diary diary = transactionTemplate.execute(status ->
                diaryRepository.findByIdAndUserId(diaryId, userId).orElse(null));
        if (diary == null) {
            log.debug("[EmotionAnalysis] diary not found userId={} diaryId={}", userId, diaryId);
            return;
        }
        if (Boolean.TRUE.equals(diary.getIsDraft())) {
            log.debug("[EmotionAnalysis] skip draft diary userId={} diaryId={}", userId, diaryId);
            return;
        }
        String mood = diary.getTodayMood() != null ? diary.getTodayMood().name() : null;
        analyze(
                userId,
                EmotionSourceType.DIARY,
                diaryId,
                diary.getCreatedAt(),
                () -> geminiService.analyzeEmotionFromDiary(diary.getTitle(), diary.getContent(), mood)
        );
    }

    @Async
    public void runDailyQuestionAnalysis(Long userId, Long userDailyQuestionId) {
        Optional<DailyQuestionEmotionSnapshot> snapshot = transactionTemplate.execute(status -> {
            return userDailyQuestionRepository.findById(userDailyQuestionId)
                    .filter(uq -> uq.getUser().getId().equals(userId))
                    .filter(uq -> !uq.isDraft())
                    .map(uq -> new DailyQuestionEmotionSnapshot(
                            uq.getDailyQuestion().getContent(),
                            uq.getContent(),
                            uq.getCreatedAt() != null ? uq.getCreatedAt() : LocalDateTime.now()
                    ));
        });
        if (snapshot == null || snapshot.isEmpty()) {
            log.debug("[EmotionAnalysis] skip daily question (not found, draft, or no access) userId={} id={}",
                    userId, userDailyQuestionId);
            return;
        }
        DailyQuestionEmotionSnapshot s = snapshot.get();
        analyze(
                userId,
                EmotionSourceType.DAILY_QUESTION,
                userDailyQuestionId,
                s.createdAt(),
                () -> geminiService.analyzeEmotionFromDailyQuestion(s.questionContent(), s.answerContent())
        );
    }

    @Async
    public void runDeepThoughtAnalysis(Long userId, Long deepThoughtId) {
        DeepThought dt = transactionTemplate.execute(status ->
                deepThoughtRepository.findByIdAndUserId(deepThoughtId, userId).orElse(null));
        if (dt == null) {
            log.debug("[EmotionAnalysis] deepThought not found userId={} id={}", userId, deepThoughtId);
            return;
        }
        if (Boolean.TRUE.equals(dt.getIsDraft())) {
            log.debug("[EmotionAnalysis] skip draft deepThought userId={} id={}", userId, deepThoughtId);
            return;
        }
        analyze(
                userId,
                EmotionSourceType.DEEP_THOUGHT,
                deepThoughtId,
                dt.getCreatedAt(),
                () -> geminiService.analyzeEmotionFromDeepThought(dt.getTitle(), dt.getContent())
        );
    }

    private void analyze(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId,
            LocalDateTime sourceCreatedAt,
            Supplier<String> geminiCall
    ) {
        emotionService.ensurePending(userId, sourceType, sourceId, sourceCreatedAt);

        for (int i = 0; i < IMMEDIATE_ATTEMPTS; i++) {
            sleepQuietly(IMMEDIATE_BACKOFF_MS[Math.min(i, IMMEDIATE_BACKOFF_MS.length - 1)]);
            emotionService.markAttemptStarted(userId, sourceType, sourceId);

            String category;
            try {
                category = geminiCall.get();
            } catch (Exception e) {
                log.error("[EmotionAnalysis] Gemini threw userId={} sourceType={} sourceId={} attempt={}",
                        userId, sourceType, sourceId, i + 1, e);
                emotionService.recordFailedAttempt(userId, sourceType, sourceId);
                continue;
            }

            if (category == null || category.isBlank()) {
                log.warn("[EmotionAnalysis] empty category userId={} sourceType={} sourceId={} attempt={}",
                        userId, sourceType, sourceId, i + 1);
                emotionService.recordFailedAttempt(userId, sourceType, sourceId);
                continue;
            }

            if (EmotionCategoryAllowlist.normalizeIfAllowed(category).isEmpty()) {
                log.warn("[EmotionAnalysis] category not in allowlist userId={} sourceType={} sourceId={} raw={} attempt={}",
                        userId, sourceType, sourceId, category, i + 1);
                emotionService.recordFailedAttempt(userId, sourceType, sourceId);
                continue;
            }

            emotionService.markSucceeded(userId, sourceType, sourceId, category);
            return;
        }
        // 즉시 재시도 소진 후에도 PENDING이면 스케줄러가 backoff 후 이어서 처리
        log.warn("[EmotionAnalysis] immediate retries exhausted userId={} sourceType={} sourceId={}",
                userId, sourceType, sourceId);
    }

    private static void sleepQuietly(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
