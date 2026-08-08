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

    /** 분석 성공 시에만 채워진다. PENDING/FAILED 는 null. */
    @Column(length = 30)
    private String emotionCategory;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20,
            columnDefinition = "varchar(20) not null default 'SUCCEEDED'"
    )
    private EmotionAnalysisStatus status = EmotionAnalysisStatus.SUCCEEDED;

    @Column(name = "retry_count", nullable = false, columnDefinition = "int not null default 0")
    private int retryCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static Emotion createPending(
            User user,
            EmotionSourceType sourceType,
            Long sourceId,
            LocalDateTime createdAt
    ) {
        Emotion emotion = new Emotion();
        emotion.user = user;
        emotion.sourceType = sourceType;
        emotion.sourceId = sourceId;
        emotion.emotionCategory = null;
        emotion.status = EmotionAnalysisStatus.PENDING;
        emotion.retryCount = 0;
        emotion.lastAttemptAt = null;
        emotion.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        return emotion;
    }

    /**
     * 기존 성공 행(마이그레이션 전 데이터)용. status 없이 카테고리만 있던 행을 성공으로 취급할 때 사용.
     */
    public static Emotion createSucceeded(
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
        emotion.status = EmotionAnalysisStatus.SUCCEEDED;
        emotion.retryCount = 0;
        emotion.lastAttemptAt = LocalDateTime.now();
        emotion.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        return emotion;
    }

    public void markPendingForRetry() {
        this.status = EmotionAnalysisStatus.PENDING;
        this.emotionCategory = null;
        this.retryCount = 0;
        this.lastAttemptAt = null;
    }

    public void markAttemptStarted() {
        this.status = EmotionAnalysisStatus.PENDING;
        this.lastAttemptAt = LocalDateTime.now();
    }

    public void markSucceeded(String emotionCategory) {
        this.emotionCategory = emotionCategory;
        this.status = EmotionAnalysisStatus.SUCCEEDED;
        this.lastAttemptAt = LocalDateTime.now();
    }

    public void recordFailedAttempt(int maxRetry) {
        this.retryCount = this.retryCount + 1;
        this.lastAttemptAt = LocalDateTime.now();
        this.emotionCategory = null;
        if (this.retryCount >= maxRetry) {
            this.status = EmotionAnalysisStatus.FAILED;
        } else {
            this.status = EmotionAnalysisStatus.PENDING;
        }
    }

    /** 레거시 행 등 status가 비어 있을 때 성공으로 간주 */
    public EmotionAnalysisStatus effectiveStatus() {
        return status != null ? status : EmotionAnalysisStatus.SUCCEEDED;
    }

    public boolean isSucceeded() {
        return effectiveStatus() == EmotionAnalysisStatus.SUCCEEDED
                && emotionCategory != null
                && !emotionCategory.isBlank();
    }
}
