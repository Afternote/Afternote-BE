package com.afternote.domain.notification.engagement;

import jakarta.persistence.*;
import java.time.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_profile")
@Getter
@NoArgsConstructor
public class NotificationProfile {
    @Id @Column(name = "user_id") private Long userId;
    @Column(name = "daily_enabled") private boolean dailyEnabled = true;
    @Column(name = "daily_time") private LocalTime dailyTime = LocalTime.of(18, 0);
    private int weekdays = 127;
    @Column(name = "weekly_goal") private int weeklyGoal = 3;
    private long revision;
    @Column(name = "delegate_name") private String delegateName;
    @Column(name = "delegate_phone") private String delegatePhone;
    @Column(name = "delegate_user_id") private Long delegateUserId;
    @Column(name = "delegate_version") private String delegateVersion;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Column(name = "confirmed_cycle") private LocalDateTime confirmedCycle;
    @Column(name = "return_pending") private boolean returnPending;
    @Column(name = "cancellation_pending") private boolean cancellationPending;
    public NotificationProfile(Long userId) { this.userId = userId; }
    public void settings(boolean enabled, LocalTime time, int weekdays, int goal) {
        dailyEnabled = enabled; dailyTime = time; this.weekdays = weekdays; weeklyGoal = goal; revision++;
    }
    public void delegate(String name, String phone, Long accountId) {
        delegateName = name; delegatePhone = phone; delegateUserId = accountId;
        delegateVersion = java.util.UUID.randomUUID().toString(); confirmedAt = null; confirmedCycle = null;
    }
    public boolean hasDelegate() { return delegatePhone != null; }
    public boolean selected(DayOfWeek day) { return (weekdays & (1 << (day.getValue() - 1))) != 0; }
    public void confirm(LocalDateTime cycle, LocalDateTime now) { confirmedCycle = cycle; confirmedAt = now; }
    public void captureActivity(LocalDateTime previous, LocalDateTime now) {
        if (previous != null && !previous.isAfter(now.minusDays(30))) returnPending = true;
        if (confirmedAt != null && now.isBefore(confirmedAt.plusDays(7))) cancellationPending = true;
        confirmedAt = null; confirmedCycle = null;
    }
    public void acknowledgeEntry() { returnPending = false; cancellationPending = false; }
}
