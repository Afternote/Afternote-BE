package com.afternote.domain.dailyquestion.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "user_daily_question")
@Getter
@NoArgsConstructor
public class UserDailyQuestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private DailyQuestion dailyQuestion;

    @Column(name = "question_date", nullable = false)
    private LocalDate questionDate;

    @Column(name = "is_answered", nullable = false)
    private boolean isAnswered;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "is_draft", nullable = false)
    private boolean isDraft;

    @Builder
    private UserDailyQuestion(
            User user,
            DailyQuestion dailyQuestion,
            LocalDate questionDate,
            boolean isAnswered,
            String content,
            String imageUrl,
            boolean isDraft
    ) {
        this.user = user;
        this.dailyQuestion = dailyQuestion;
        this.questionDate = questionDate;
        this.isAnswered = isAnswered;
        this.content = content;
        this.imageUrl = imageUrl;
        this.isDraft = isDraft;
    }

    public void updateAnswer(String content, String imageUrl, boolean isDraft) {
        this.content = content;
        this.imageUrl = imageUrl;
        this.isDraft = isDraft;
        this.isAnswered = !isDraft;
    }
}
