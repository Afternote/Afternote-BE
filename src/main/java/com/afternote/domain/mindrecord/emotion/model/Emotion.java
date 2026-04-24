package com.afternote.domain.mindrecord.emotion.model;

import com.afternote.domain.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "emotions",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "source_type", "source_id"})
    }
)
@Getter
@NoArgsConstructor
public class Emotion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private EmotionSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(length = 30, nullable = false)
    private String emotionCategory;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Emotion create(
            User user,
            EmotionSourceType sourceType,
            Long sourceId,
            String emotionCategory,
            LocalDateTime createdAt
    ) {
        Emotion emotion = new Emotion();
        emotion.user = user;
        emotion.sourceType = sourceType;
        emotion.sourceId = sourceId;
        emotion.emotionCategory = emotionCategory;
        emotion.createdAt = createdAt;
        return emotion;
    }

    public void updateEmotionCategory(String emotionCategory) {
        if (emotionCategory != null && !emotionCategory.isBlank()) {
            this.emotionCategory = emotionCategory;
        }
    }
}
