package com.afternote.domain.notification.policy;

public enum NotificationEvent {
    SCHEDULED, ACCEPTANCE, PAYMENT_FAILURE, DELIVERY_COMPLETE, SEND_FAILURE;
    public boolean isImmediate() { return this != SCHEDULED; }
}
