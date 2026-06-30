package com.afternote.domain.delivery.dto;

import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.model.InactivityPeriod;
import com.afternote.domain.user.model.DeliveryConditionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "콘텐츠 타입별 전달 조건 응답")
public record DeliveryConditionItemResponse(
        @Schema(description = "콘텐츠 타입") DeliveryContentType contentType,
        @Schema(description = "전달 조건 타입") DeliveryConditionType conditionType,
        @Schema(description = "미사용 기간", nullable = true) InactivityPeriod inactivityPeriod,
        @Schema(description = "조건 상태") ConditionState state,
        @Schema(description = "충족(열람 가능) 여부") boolean fulfilled,
        @Schema(description = "본인확인 유예 시작 시각", nullable = true) LocalDateTime gracePeriodStartedAt,
        @Schema(description = "충족 시각", nullable = true) LocalDateTime fulfilledAt
) {
    public static DeliveryConditionItemResponse from(DeliveryCondition condition) {
        return new DeliveryConditionItemResponse(
                condition.getContentType(),
                condition.getConditionType(),
                condition.getInactivityPeriod(),
                condition.getState(),
                condition.isFulfilled(),
                condition.getGracePeriodStartedAt(),
                condition.getFulfilledAt()
        );
    }
}
