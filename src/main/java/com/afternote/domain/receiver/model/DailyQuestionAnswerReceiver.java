package com.afternote.domain.receiver.model;

import com.afternote.domain.mindrecord.question.model.DailyQuestionAnswer;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "daily_question_answer_receiver", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"daily_question_answer_id", "receiver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyQuestionAnswerReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_question_answer_id", nullable = false)
    private DailyQuestionAnswer dailyQuestionAnswer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public DailyQuestionAnswerReceiver(DailyQuestionAnswer dailyQuestionAnswer, Receiver receiver) {
        this.dailyQuestionAnswer = dailyQuestionAnswer;
        this.receiver = receiver;
    }
}