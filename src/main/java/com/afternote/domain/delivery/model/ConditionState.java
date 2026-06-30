package com.afternote.domain.delivery.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "전달 조건의 현재 상태")
public enum ConditionState {
    @Schema(description = "활성 - 미사용 감지 대기 중")
    ACTIVE,
    @Schema(description = "본인확인 대기 - 미사용 기간 도달, 7일 유예 진행 중")
    PENDING_CONFIRMATION,
    @Schema(description = "서류 검증 대기 - 수신자 요청(운영자 승인) 대기 중")
    WAITING_VERIFICATION,
    @Schema(description = "충족 - 수신자가 열람 가능")
    FULFILLED
}
