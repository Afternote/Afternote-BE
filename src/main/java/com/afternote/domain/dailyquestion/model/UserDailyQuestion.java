package com.afternote.domain.dailyquestion.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    public static UserDailyQuestion create(
            User user,
            DailyQuestion dailyQuestion,
            String content,
            String imageUrl
    ) {
        UserDailyQuestion userDailyQuestion = new UserDailyQuestion();
        userDailyQuestion.user = user;
        userDailyQuestion.dailyQuestion = dailyQuestion;
        userDailyQuestion.content = content;
        userDailyQuestion.imageUrl = imageUrl;
        return userDailyQuestion;
    }
}
