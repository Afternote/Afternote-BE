package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmotionService {

    private final EmotionRepository emotionRepository;
    private final UserRepository userRepository;

    /**
     * Gemini 등으로 분석된 감정 문자열을 저장한다.
     * 클래스의 readOnly 기본값과 무관하게 쓰기 가능한 새 트랜잭션에서 커밋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void persistAnalyzedEmotion(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId,
            String analyzedCategory,
            LocalDateTime sourceCreatedAt
    ) {
        try {
            upsertInTransaction(userId, sourceType, sourceId, analyzedCategory, sourceCreatedAt);
        } catch (Exception e) {
            log.error(
                    "[Emotion] persist failed userId={} sourceType={} sourceId={}",
                    userId,
                    sourceType,
                    sourceId,
                    e
            );
        }
    }

    private void upsertInTransaction(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId,
            String emotionCategory,
            LocalDateTime sourceCreatedAt
    ) {
        String category = normalizeAnalyzedCategory(emotionCategory);
        if (category == null || category.isBlank()) {
            log.debug(
                    "[Emotion] skip (no category) userId={} sourceType={} sourceId={}",
                    userId,
                    sourceType,
                    sourceId
            );
            return;
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[Emotion] skip (user not found) userId={} sourceType={} sourceId={}", userId, sourceType, sourceId);
            return;
        }

        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (existing.isEmpty()) {
            Emotion created = Emotion.create(
                    user,
                    sourceType,
                    sourceId,
                    category,
                    sourceCreatedAt != null ? sourceCreatedAt : LocalDateTime.now()
            );
            emotionRepository.save(created);
            log.info(
                    "[Emotion] created emotionId={} userId={} sourceType={} sourceId={} category={}",
                    created.getId(),
                    userId,
                    sourceType,
                    sourceId,
                    category
            );
            return;
        }

        Emotion emotion = existing.get();
        String before = emotion.getEmotionCategory();
        emotion.updateEmotionCategory(category);
        emotionRepository.save(emotion);
        if (Objects.equals(before, category)) {
            log.info(
                    "[Emotion] saved (category unchanged) emotionId={} userId={} sourceType={} sourceId={} category={}",
                    emotion.getId(),
                    userId,
                    sourceType,
                    sourceId,
                    category
            );
        } else {
            log.info(
                    "[Emotion] updated emotionId={} userId={} sourceType={} sourceId={} categoryBefore={} categoryAfter={}",
                    emotion.getId(),
                    userId,
                    sourceType,
                    sourceId,
                    before,
                    category
            );
        }
    }

    /** DB 컬럼 길이(30) 및 공백 정리 */
    private String normalizeAnalyzedCategory(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > 30 ? s.substring(0, 30) : s;
    }
}
