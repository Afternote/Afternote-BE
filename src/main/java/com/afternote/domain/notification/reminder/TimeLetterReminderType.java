package com.afternote.domain.notification.reminder;

import java.util.Map;

/** Server-owned copy and H navigation contract for the first production notification types. */
public enum TimeLetterReminderType {
    DAYS_30(30, "TIME_LETTER_REMINDER_30D", "VIEW"),
    DAYS_7(7, "TIME_LETTER_REMINDER_7D", "EDIT");

    private final int days;
    private final String eventType;
    private final String action;

    TimeLetterReminderType(int days, String eventType, String action) {
        this.days = days;
        this.eventType = eventType;
        this.action = action;
    }

    public int days() { return days; }
    public String eventType() { return eventType; }
    public String title() { return "AfterNote"; }
    public String body(String receiverName) {
        return this == DAYS_7
                ? "예약하신 편지가 곧 전달돼요. 지금이면 수정할 수 있어요"
                : receiverName + "님께 갈 편지가 한 달 뒤에 전달돼요";
    }
    public Map<String, String> data(Long letterId, java.time.LocalDateTime sendAt) {
        return Map.of("type", eventType, "destination", "TIME_LETTER_DETAIL",
                "timeLetterId", letterId.toString(), "action", action, "scheduledSendAt", sendAt.toString());
    }
    public static TimeLetterReminderType fromEventType(String value) {
        for (TimeLetterReminderType type : values()) if (type.eventType.equals(value)) return type;
        return null;
    }
}
