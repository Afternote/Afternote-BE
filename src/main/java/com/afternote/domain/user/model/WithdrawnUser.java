package com.afternote.domain.user.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 탈퇴 이력. hard delete 후에도 동일 이메일 재가입 쿨다운(30일) 판단에 사용한다.
 */
@Entity
@Table(
        name = "withdrawn_user",
        indexes = {
                @Index(name = "idx_withdrawn_user_email_withdrawn_at", columnList = "email, withdrawn_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawnUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    @Column(name = "previous_user_id")
    private Long previousUserId;

    public static WithdrawnUser of(String email, Long previousUserId) {
        WithdrawnUser row = new WithdrawnUser();
        row.email = email;
        row.previousUserId = previousUserId;
        row.withdrawnAt = LocalDateTime.now();
        return row;
    }
}
