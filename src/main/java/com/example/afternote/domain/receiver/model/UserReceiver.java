package com.example.afternote.domain.receiver.model;

import com.example.afternote.domain.user.model.User; // User 패키지 경로는 실제 구조에 맞게 수정 필요
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_receiver") // 테이블명: user_receiver (일반적인 관례) 혹은 userreceiver
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserReceiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 생성시점: DATETIME, NOT NULL
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 유저ID: BIGINT, NOT NULL -> User 객체와 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 수신자ID: BIGINT, NOT NULL -> Receiver 객체와 매핑
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @Builder
    public UserReceiver(User user, Receiver receiver) {
        this.user = user;
        this.receiver = receiver;
    }
}