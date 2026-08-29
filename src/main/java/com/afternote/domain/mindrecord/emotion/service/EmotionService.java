package com.afternote.domain.mindrecord.emotion.service;

import com.afternote.domain.mindrecord.emotion.EmotionCategoryAllowlist;
import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionAnalysisStatus;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmotionService {

    public static final int DEFAULT_MAX_RETRY = 3;

    private final EmotionRepository emotionRepository;
    private final UserRepository userRepository;

    @Value("${afternote.emotion-analysis.max-retry:3}")
    private int maxRetry = DEFAULT_MAX_RETRY;

    public int getMaxRetry() {
        return maxRetry > 0 ? maxRetry : DEFAULT_MAX_RETRY;
    }

    /**
     * 분석 대상 행을 PENDING으로 보장한다. 이미 SUCCEEDED여도 재분석 시 PENDING으로 리셋한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void ensurePending(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId,
            LocalDateTime sourceCreatedAt
    ) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("[Emotion] ensurePending skip (user not found) userId={} sourceType={} sourceId={}",
                    userId, sourceType, sourceId);
            return;
        }

        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (existing.isEmpty()) {
            Emotion created = Emotion.createPending(user, sourceType, sourceId, sourceCreatedAt);
            emotionRepository.save(created);
            log.info("[Emotion] pending created emotionId={} userId={} sourceType={} sourceId={}",
                    created.getId(), userId, sourceType, sourceId);
            return;
        }

        Emotion emotion = existing.get();
        if (emotion.getStatus() != EmotionAnalysisStatus.PENDING || emotion.getEmotionCategory() != null) {
            emotion.markPendingForRetry();
            emotionRepository.save(emotion);
            log.info("[Emotion] reset to pending emotionId={} userId={} sourceType={} sourceId={}",
                    emotion.getId(), userId, sourceType, sourceId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void markAttemptStarted(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId
    ) {
        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (existing.isEmpty()) {
            return;
        }
        Emotion emotion = existing.get();
        emotion.markAttemptStarted();
        emotionRepository.save(emotion);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void markSucceeded(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId,
            String analyzedCategory
    ) {
        Optional<String> allowed = EmotionCategoryAllowlist.normalizeIfAllowed(analyzedCategory);
        if (allowed.isEmpty()) {
            log.warn("[Emotion] reject non-allowlist category userId={} sourceType={} sourceId={} raw={}",
                    userId, sourceType, sourceId, analyzedCategory);
            recordFailedAttempt(userId, sourceType, sourceId);
            return;
        }

        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (existing.isEmpty()) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }
            Emotion created = Emotion.createPending(user, sourceType, sourceId, LocalDateTime.now());
            created.markSucceeded(allowed.get());
            emotionRepository.save(created);
            log.info("[Emotion] succeeded (created) emotionId={} category={}", created.getId(), allowed.get());
            return;
        }

        Emotion emotion = existing.get();
        emotion.markSucceeded(allowed.get());
        emotionRepository.save(emotion);
        log.info("[Emotion] succeeded emotionId={} userId={} sourceType={} sourceId={} category={}",
                emotion.getId(), userId, sourceType, sourceId, allowed.get());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public void recordFailedAttempt(
            Long userId,
            EmotionSourceType sourceType,
            Long sourceId
    ) {
        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (existing.isEmpty()) {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                return;
            }
            Emotion created = Emotion.createPending(user, sourceType, sourceId, LocalDateTime.now());
            created.recordFailedAttempt(getMaxRetry());
            emotionRepository.save(created);
            log.warn("[Emotion] failed attempt (created) status={} retryCount={} userId={} sourceType={} sourceId={}",
                    created.getStatus(), created.getRetryCount(), userId, sourceType, sourceId);
            return;
        }

        Emotion emotion = existing.get();
        emotion.recordFailedAttempt(getMaxRetry());
        emotionRepository.save(emotion);
        log.warn("[Emotion] failed attempt emotionId={} status={} retryCount={} userId={} sourceType={} sourceId={}",
                emotion.getId(), emotion.getStatus(), emotion.getRetryCount(), userId, sourceType, sourceId);
    }

    /** 당일 시도 상한에 여유가 있으면 true. FAILED이거나 retryCount가 max면 false. */
    public boolean hasRetryBudget(Long userId, EmotionSourceType sourceType, Long sourceId) {
        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (existing.isEmpty()) {
            return true;
        }
        Emotion emotion = existing.get();
        if (emotion.effectiveStatus() == EmotionAnalysisStatus.FAILED) {
            return false;
        }
        return emotion.getRetryCount() < getMaxRetry();
    }

    /**
     * 새벽 스윕: FAILED를 PENDING으로 되돌린다. 24시간 쿨다운을 우회하려면 러너 호출 전에 이 메서드를 쓴다.
     * @return 실제로 리셋했으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    public boolean reviveFailed(Long userId, EmotionSourceType sourceType, Long sourceId) {
        Optional<Emotion> existing = emotionRepository.findByUserIdAndSourceTypeAndSourceId(userId, sourceType, sourceId);
        if (existing.isEmpty() || existing.get().effectiveStatus() != EmotionAnalysisStatus.FAILED) {
            return false;
        }
        Emotion emotion = existing.get();
        emotion.markPendingForRetry();
        emotionRepository.save(emotion);
        log.info("[Emotion] dawn revive emotionId={} userId={} sourceType={} sourceId={}",
                emotion.getId(), userId, sourceType, sourceId);
        return true;
    }

    public record RetryCandidate(Long userId, EmotionSourceType sourceType, Long sourceId) {}

    /**
     * 백필 대상: PENDING 이면서 재시도 여유가 있고 backoff가 지난 행.
     */
    @Transactional(readOnly = true)
    public List<RetryCandidate> findRetryCandidates(int limit) {
        int batch = Math.max(1, limit);
        LocalDateTime eligibleBefore = LocalDateTime.now().minusMinutes(1);
        List<Emotion> rows = emotionRepository.findRetryCandidates(
                EmotionAnalysisStatus.PENDING,
                getMaxRetry(),
                eligibleBefore,
                PageRequest.of(0, batch * 3)
        );
        return rows.stream()
                .filter(this::isBackoffElapsed)
                .limit(batch)
                .map(e -> new RetryCandidate(e.getUser().getId(), e.getSourceType(), e.getSourceId()))
                .toList();
    }

    /** 새벽용 PENDING. backoff·1분 대기 없이 남은 당일 시도를 소진한다. */
    @Transactional(readOnly = true)
    public List<RetryCandidate> findPendingForDawn(int limit) {
        int batch = Math.max(1, limit);
        List<Emotion> rows = emotionRepository.findRetryCandidates(
                EmotionAnalysisStatus.PENDING,
                getMaxRetry(),
                LocalDateTime.now(),
                PageRequest.of(0, batch)
        );
        return rows.stream()
                .map(e -> new RetryCandidate(e.getUser().getId(), e.getSourceType(), e.getSourceId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RetryCandidate> findFailedForDawn(int limit) {
        int batch = Math.max(1, limit);
        return emotionRepository.findByStatusForDawn(
                        EmotionAnalysisStatus.FAILED,
                        PageRequest.of(0, batch)
                ).stream()
                .map(e -> new RetryCandidate(e.getUser().getId(), e.getSourceType(), e.getSourceId()))
                .toList();
    }

    private boolean isBackoffElapsed(Emotion emotion) {
        if (emotion.getLastAttemptAt() == null) {
            return true;
        }
        long minutes = Math.min(1L << Math.min(emotion.getRetryCount(), 6), 60L);
        return !emotion.getLastAttemptAt().plusMinutes(minutes).isAfter(LocalDateTime.now());
    }
}
