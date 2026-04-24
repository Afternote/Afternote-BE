package com.afternote.domain.receiver.model;

import com.afternote.domain.mindrecord.question.model.UserDailyQuestion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_daily_question_receiver", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_daily_question_id", "receiver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyQuestionReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_daily_question_id", nullable = false)
    private UserDailyQuestion userDailyQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserDailyQuestionReceiver(UserDailyQuestion userDailyQuestion, Receiver receiver) {
        this.userDailyQuestion = userDailyQuestion;
        this.receiver = receiver;
    }
}