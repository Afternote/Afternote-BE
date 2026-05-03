package com.afternote.domain.mindrecord.emotion.event;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.deepthought.model.DeepThought;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionAnalysisRunner {

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
        String category = geminiService.analyzeEmotionFromDiary(diary.getTitle(), diary.getContent(), mood);
        if (category == null || category.isBlank()) {
            log.warn("[EmotionAnalysis] no category from Gemini (diary) userId={} diaryId={}", userId, diaryId);
            return;
        }
        emotionService.persistAnalyzedEmotion(
                userId,
                EmotionSourceType.DIARY,
                diaryId,
                category,
                diary.getCreatedAt()
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
        String category = geminiService.analyzeEmotionFromDailyQuestion(s.questionContent(), s.answerContent());
        if (category == null || category.isBlank()) {
            log.warn("[EmotionAnalysis] no category from Gemini (dailyQuestion) userId={} id={}", userId, userDailyQuestionId);
            return;
        }
        emotionService.persistAnalyzedEmotion(
                userId,
                EmotionSourceType.DAILY_QUESTION,
                userDailyQuestionId,
                category,
                s.createdAt()
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
        String category = geminiService.analyzeEmotionFromDeepThought(dt.getTitle(), dt.getContent());
        if (category == null || category.isBlank()) {
            log.warn("[EmotionAnalysis] no category from Gemini (deepThought) userId={} id={}", userId, deepThoughtId);
            return;
        }
        emotionService.persistAnalyzedEmotion(
                userId,
                EmotionSourceType.DEEP_THOUGHT,
                deepThoughtId,
                category,
                dt.getCreatedAt()
        );
    }
}
