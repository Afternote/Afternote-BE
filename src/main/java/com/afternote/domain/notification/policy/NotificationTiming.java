package com.afternote.domain.notification.policy;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public final class NotificationTiming {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private NotificationTiming() { }
    public static LocalDateTime allowedAt(NotificationCategory category, LocalDateTime requested) {
        if (category == NotificationCategory.B) return requested;
        if (requested.getHour() < 8) return requested.toLocalDate().atTime(8, 0);
        if (requested.getHour() >= 22) return requested.toLocalDate().plusDays(1).atTime(LocalTime.of(8, 0));
        return requested;
    }
}
