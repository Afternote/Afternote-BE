package com.afternote.domain.mindrecord.question.model;

import com.afternote.domain.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_daily_question")
@Getter
@NoArgsConstructor
public class UserDailyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private DailyQuestion dailyQuestion;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(length = 10)
    private String emotion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static UserDailyQuestion create(
            User user,
            DailyQuestion dailyQuestion,
            String content,
            String imageUrl,
            String emotion
    ) {
        UserDailyQuestion userDailyQuestion = new UserDailyQuestion();
        userDailyQuestion.user = user;
        userDailyQuestion.dailyQuestion = dailyQuestion;
        userDailyQuestion.content = content;
        userDailyQuestion.imageUrl = imageUrl;
        userDailyQuestion.emotion = emotion;
        return userDailyQuestion;
    }
}