package com.afternote.domain.delivery.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사후 전달 조건이 적용되는 콘텐츠 타입")
public enum DeliveryContentType {
    @Schema(description = "타임레터 (POST_DEATH 모드인 경우에만 조건 적용)")
    TIME_LETTER,
    @Schema(description = "애프터노트")
    AFTERNOTE,
    @Schema(description = "데일리 질문")
    DAILY_QUESTION,
    @Schema(description = "다이어리")
    DIARY,
    @Schema(description = "딥쏘트")
    DEEP_THOUGHT
}
