package com.afternote.domain.notification.engagement;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Entity
@Getter
@NoArgsConstructor
@Table(name = "notification_link")
public class NotificationLink {
    @Id @Column(name = "token_hash", length = 64) private String tokenHash;
    @Column(name = "user_id") private Long userId;
    private String purpose;
    private LocalDateTime cycle;
    @Column(name = "delegate_version") private String delegateVersion;
    @Column(name = "expires_at") private LocalDateTime expiresAt;
    @Column(name = "consumed_at") private LocalDateTime consumedAt;
    public NotificationLink(String hash, Long owner, String purpose, LocalDateTime cycle, String version, LocalDateTime expires) {
        tokenHash = hash; userId = owner; this.purpose = purpose; this.cycle = cycle; delegateVersion = version; expiresAt = expires;
    }
    public void consume(LocalDateTime at) { consumedAt = at; }
}
