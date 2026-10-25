package com.afternote.domain.notification.engagement;

import com.afternote.domain.notification.policy.*;
/** Non-billing copy and navigation catalog. IDs are added by the source-specific producer. */
public enum NotificationKind {
    INACTIVE_6(NotificationCategory.A, "RECORD_LIST", "VIEW", NotificationChannel.PUSH, "6개월 만이에요. 남기신 기록 %s개가 그대로 있어요"),
    INACTIVE_12(NotificationCategory.A, "AGENT_SETTINGS", "VIEW", NotificationChannel.PUSH_AND_ALIMTALK, "1년 넘게 들어오지 않으셨어요. 대리인을 지정해 두시면 더 안전해요"),
    INACTIVE_18(NotificationCategory.A, "RETENTION_CONFIRMATION", "VIEW", NotificationChannel.PUSH_AND_ALIMTALK, "기록을 계속 보관할지 확인해 주세요. 확인이 안 되면 지정하신 분께 연락드릴 수 있어요"),
    DELEGATE_CONTACT(NotificationCategory.A, "AGENT_CONFIRMATION", "VIEW", NotificationChannel.PUSH_AND_ALIMTALK, "%s님이 오래 들어오지 않으셨어요. 연락이 닿으시면 알려주세요"),
    RETENTION_WARNING(NotificationCategory.A, "RETENTION_CONFIRMATION", "CANCEL_PROCEDURE", NotificationChannel.PUSH_AND_ALIMTALK, "지금 앱을 열면 진행 중인 절차가 모두 취소됩니다"),
    DAILY(NotificationCategory.B, "DAILY_QUESTION", "VIEW", NotificationChannel.PUSH, "오늘의 질문이 도착했어요. 답변을 남겨볼까요?"),
    FIRST_NEXT_DAY(NotificationCategory.B, "DAILY_QUESTION", "VIEW", NotificationChannel.PUSH, "어제 첫 기록을 남기셨어요. 오늘 질문도 준비해 뒀어요"),
    WEEKLY_PROGRESS(NotificationCategory.B, "DAILY_QUESTION", "WEEKLY_PROGRESS", NotificationChannel.PUSH, "이번 주에 %s번 남기셨어요."),
    FOUR_WEEKS(NotificationCategory.B, "WEEKLY_REPORT", "VIEW", NotificationChannel.PUSH, "한 달 동안 매주 채우셨어요"),
    LAST_YEAR(NotificationCategory.C, "RECORD_DETAIL", "VIEW", NotificationChannel.PUSH, "작년 오늘 이런 기록을 남기셨어요"),
    THREE_MONTHS(NotificationCategory.C, "RECORD_DETAIL", "VIEW", NotificationChannel.PUSH, "3개월 전 오늘의 기록이에요"),
    FIRST_ANNIVERSARY(NotificationCategory.C, "RECORD_DETAIL", "VIEW", NotificationChannel.PUSH, "첫 기록을 남기신 지 1년이 됐어요"),
    UNDELIVERED(NotificationCategory.C, "EASY_QUESTIONS", "VIEW", NotificationChannel.PUSH, "%s님께 아직 전하지 못한 기록이 %s개 있어요"),
    RECORD_COUNT(NotificationCategory.C, "EASY_QUESTIONS", "VIEW", NotificationChannel.PUSH, "지금까지 남기신 기록이 %s개예요"),
    NO_DELEGATE(NotificationCategory.C, "AGENT_SETTINGS", "VIEW", NotificationChannel.PUSH, "대리인을 아직 지정하지 않으셨어요"),
    ANNIVERSARY(NotificationCategory.C, "TIME_LETTER_COMPOSE", "VIEW", NotificationChannel.PUSH, "오늘은 %s님 생신이에요. 편지를 남겨보시겠어요?");
    public final NotificationCategory category;
    public final String destination;
    public final String action;
    public final NotificationChannel channel;
    private final String copy;
    NotificationKind(NotificationCategory category, String destination, String action, NotificationChannel channel, String copy) {
        this.category = category; this.destination = destination; this.action = action; this.channel = channel; this.copy = copy;
    }
    public String body(Object... args) { return String.format(copy, args); }
    public java.util.Map<String, String> data() {
        var data = new java.util.HashMap<String, String>();
        data.put("type", name()); data.put("destination", destination); data.put("action", action); return data;
    }
}
