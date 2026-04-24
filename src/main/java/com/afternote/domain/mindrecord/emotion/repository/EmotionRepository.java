package com.afternote.domain.mindrecord.emotion.repository;

import com.afternote.domain.mindrecord.emotion.model.Emotion;
import com.afternote.domain.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmotionRepository extends JpaRepository<Emotion, Long> {
    
    List<Emotion> findByUserId(Long userId);

    List<Emotion> findByUserIdAndCreatedAtAfter(Long userId, LocalDateTime startDate);
}
