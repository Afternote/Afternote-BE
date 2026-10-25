package com.afternote.domain.notification.channel;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Entity
@Getter
@NoArgsConstructor
@Table(name = "notification_channel_attempt", uniqueConstraints = @UniqueConstraint(name = "uk_notification_channel", columnNames = {"dispatch_id", "channel"}),
        indexes = @Index(name = "idx_channel_retry", columnList = "next_attempt_at,id"))
public class NotificationChannelAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "dispatch_id") private Long dispatchId;
    private String channel;
    private String status;
    @Column(name = "provider_message_id") private String providerMessageId;
    private int attempts;
    @Column(name = "next_attempt_at") private LocalDateTime nextAttemptAt;
    public NotificationChannelAttempt(Long dispatch, String channel) { dispatchId = dispatch; this.channel = channel; status = "PENDING"; }
    public void claim() { attempts++; status = "SENDING"; nextAttemptAt = null; }
    public void result(String state, String providerId, LocalDateTime retryAt) {
        status = state; providerMessageId = providerId; nextAttemptAt = retryAt;
    }
}
