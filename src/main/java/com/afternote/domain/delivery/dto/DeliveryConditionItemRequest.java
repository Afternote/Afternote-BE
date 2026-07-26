package com.afternote.domain.delivery.dto;

import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.model.InactivityPeriod;
import com.afternote.domain.user.model.DeliveryConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Schema(description = "콘텐츠 타입별 전달 조건 항목")
public record DeliveryConditionItemRequest(
        @NotNull(message = "콘텐츠 타입은 필수입니다.")
        @Schema(description = "콘텐츠 타입", example = "AFTERNOTE")
        @Getter
        DeliveryContentType contentType,

        @NotNull(message = "전달 조건 타입은 필수입니다.")
        @Schema(description = "전달 조건 타입", example = "INACTIVITY")
        @Getter
        DeliveryConditionType conditionType,

        @Schema(description = "미사용 기간 (INACTIVITY일 때 필수)", example = "ONE_YEAR", nullable = true)
        @Getter
        InactivityPeriod inactivityPeriod
) {



}
