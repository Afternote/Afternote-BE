package com.afternote.domain.notification.dto;

/** FCM acceptance counts; does not assert the user read or received the notification. */
public record PushDeliveryResult(int sentCount, int failedCount, boolean permissionDenied, boolean configured) {
    public boolean delivered() { return sentCount > 0; }
}
