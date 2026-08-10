package com.afternote.domain.mindrecord.weekly.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주간 캘린더 week[] 아이템 타입")
public enum WeekRecordType {
    DIARY,
    DAILY_QUESTION,
    DEEP_THOUGHT
}
