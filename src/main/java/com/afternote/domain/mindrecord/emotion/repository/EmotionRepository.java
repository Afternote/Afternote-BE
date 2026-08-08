package com.afternote.domain.mindrecord.emotion.repository;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionAnalysisStatus;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmotionRepository extends JpaRepository<Emotion, Long> {

    List<Emotion> findByUserId(Long userId);

    List<Emotion> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime startDate);

    Optional<Emotion> findByUserIdAndSourceTypeAndSourceId(Long userId, EmotionSourceType sourceType, Long sourceId);

    List<Emotion> findByUserIdAndSourceTypeAndSourceIdIn(Long userId, EmotionSourceType sourceType, List<Long> sourceIds);

    List<Emotion> findByUserIdAndSourceTypeAndSourceIdInAndStatus(
            Long userId,
            EmotionSourceType sourceType,
            List<Long> sourceIds,
            EmotionAnalysisStatus status
    );

    @Query("""
            SELECT e FROM Emotion e JOIN FETCH e.user
            WHERE e.status = :status
              AND e.retryCount < :maxRetry
              AND (e.lastAttemptAt IS NULL OR e.lastAttemptAt <= :eligibleBefore)
            ORDER BY CASE WHEN e.lastAttemptAt IS NULL THEN 0 ELSE 1 END, e.lastAttemptAt ASC
            """)
    List<Emotion> findRetryCandidates(
            @Param("status") EmotionAnalysisStatus status,
            @Param("maxRetry") int maxRetry,
            @Param("eligibleBefore") LocalDateTime eligibleBefore,
            Pageable pageable
    );

    void deleteByUser_Id(Long userId);
}
