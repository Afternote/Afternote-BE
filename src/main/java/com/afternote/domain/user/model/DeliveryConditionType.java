package com.afternote.domain.user.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사후 전달 조건 타입")
public enum DeliveryConditionType {
    @Schema(description = "미사용 자동 전달 - 지정한 기간 동안 활동이 없으면 본인확인 후 전달")
    INACTIVITY,
    @Schema(description = "수신자 요청 - 수신자가 서류 제출 후 운영자 승인 시 전달")
    RECEIVER_REQUEST
}
