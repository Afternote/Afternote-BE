package com.afternote.domain.notification.policy;

import java.time.LocalDateTime;

/** Revalidate source data before reserving the daily slot and immediately before dispatch. */
public interface NotificationDispatchGuard {
    boolean allows(NotificationDispatch notification, LocalDateTime now);
}
