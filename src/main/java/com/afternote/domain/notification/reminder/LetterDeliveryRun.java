package com.afternote.domain.notification.reminder;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
@Entity
@Getter
@NoArgsConstructor
@Table(name = "notification_letter_delivery_run", indexes = @Index(name = "idx_letter_delivery_run", columnList = "letter_id"))
public class LetterDeliveryRun {
    @Id @Column(name = "run_key", length = 160) private String runKey;
    @Column(name = "letter_id") private Long letterId;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "delivered_count") private int deliveredCount;
    @Column(name = "failed_count") private int failedCount;
    public LetterDeliveryRun(String key, Long letterId, LocalDateTime at, int delivered, int failed) {
        runKey = key; this.letterId = letterId; completedAt = at; deliveredCount = delivered; failedCount = failed;
    }
}
