package com.afternote.domain.receiver.model;

import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "receiver_invitation",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_receiver_invitation_token_hash",
                columnNames = "token_hash"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiverInvitation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inviter_user_id", nullable = false)
    private Long inviterUserId;

    @Column(name = "accepted_user_id")
    private Long acceptedUserId;

    @Column(name = "receiver_id")
    private Long receiverId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiverInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    public ReceiverInvitation(Long inviterUserId, String tokenHash, LocalDateTime expiresAt) {
        this.inviterUserId = inviterUserId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.status = ReceiverInvitationStatus.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void accept(Long userId, Long receiverId, LocalDateTime acceptedAt) {
        this.acceptedUserId = userId;
        this.receiverId = receiverId;
        this.acceptedAt = acceptedAt;
        this.status = ReceiverInvitationStatus.ACCEPTED;
    }
}
