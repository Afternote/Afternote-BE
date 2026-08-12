package com.afternote.domain.receiver.model;

import com.afternote.domain.dailyquestion.model.UserDailyQuestion;
import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_daily_question_receiver", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_daily_question_id", "receiver_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDailyQuestionReceiver extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_daily_question_id", nullable = false)
    private UserDailyQuestion userDailyQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @Builder
    public UserDailyQuestionReceiver(UserDailyQuestion userDailyQuestion, Receiver receiver) {
        this.userDailyQuestion = userDailyQuestion;
        this.receiver = receiver;
    }
}
