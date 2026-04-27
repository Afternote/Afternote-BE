package com.afternote.domain.mindrecord.emotion.repository;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.mindrecord.emotion.model.EmotionSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
