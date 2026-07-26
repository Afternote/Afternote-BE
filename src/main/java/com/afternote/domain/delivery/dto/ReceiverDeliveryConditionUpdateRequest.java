package com.afternote.domain.delivery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Schema(description = "수신자별 전달 조건 설정 요청")
public record ReceiverDeliveryConditionUpdateRequest(
        @Valid
        @NotEmpty(message = "전달 조건은 최소 1개 이상 필요합니다.")
        @Schema(description = "콘텐츠 타입별 전달 조건 목록")
        @Getter
        List<DeliveryConditionItemRequest> conditions
) {

}
