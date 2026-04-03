package com.example.afternote.domain.timeletter.model;

public enum TimeLetterStatus {
    DRAFT,      // 작성 중
    SCHEDULED,  // 발송 대기 (예약됨)
    SENT        // 발송 완료
}