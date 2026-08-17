package com.afternote.domain.notification.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_push_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_push_tokens_token", columnNames = "token")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPushToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PushPlatform platform;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Builder
    public UserPushToken(User user, String token, PushPlatform platform, LocalDateTime lastSeenAt) {
        this.user = user;
        this.token = token;
        this.platform = platform;
        this.lastSeenAt = lastSeenAt;
    }

    public void reassignTo(User user, PushPlatform platform, LocalDateTime lastSeenAt) {
        this.user = user;
        this.platform = platform;
        this.lastSeenAt = lastSeenAt;
    }

    public void touch(PushPlatform platform, LocalDateTime lastSeenAt) {
        this.platform = platform;
        this.lastSeenAt = lastSeenAt;
    }
}
