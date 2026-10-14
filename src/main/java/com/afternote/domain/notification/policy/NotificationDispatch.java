package com.afternote.domain.notification.policy;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "notification_dispatch", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_user_event", columnNames = {"user_id", "event_key"}),
        indexes = @Index(name = "idx_notification_due", columnList = "status,due_at"))
public class NotificationDispatch {
    public enum Status { QUEUED, READY, DISPATCHING, PUSH_SENT, PUSH_FAILED, ALIMTALK_PENDING, IN_APP_ONLY, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "event_key", nullable = false, length = 160)
    private String eventKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NotificationCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private NotificationEvent event;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 1000)
    private String body;
    @Column(nullable = false, length = 4000)
    private String payloadJson;
    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status;
    private LocalDate selectedDate;
    private LocalDateTime readAt;
    private LocalDateTime visibleAt;
    private String deliveryReason;

    public NotificationDispatch(Long userId, String key, NotificationCategory category, NotificationEvent event,
            String title, String body, String payloadJson, LocalDateTime dueAt) {
        this.userId = userId; this.eventKey = key; this.category = category; this.event = event;
        this.title = title; this.body = body; this.payloadJson = payloadJson; this.dueAt = dueAt;
        this.status = Status.QUEUED;
    }
    public void select(LocalDateTime now) {
        status = Status.READY;
        visibleAt = now;
        if (!event.isImmediate()) selectedDate = now.toLocalDate();
    }
    public void inAppOnly(LocalDateTime now) { status = Status.IN_APP_ONLY; visibleAt = now; }
    public void finish(Status status, String reason) { this.status = status; this.deliveryReason = reason; }
    public void defer(LocalDateTime next) { dueAt = next; }
    public void read(LocalDateTime now) { if (readAt == null) readAt = now; }
}
