package com.afternote.domain.delivery.dto;

import com.afternote.domain.delivery.model.DeliveryCondition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "수신자별 전달 조건 응답")
public record ReceiverDeliveryConditionResponse(
        @Schema(description = "수신자 ID") Long receiverId,
        @Schema(description = "콘텐츠 타입별 전달 조건 목록") List<DeliveryConditionItemResponse> conditions
) {
    public static ReceiverDeliveryConditionResponse from(Long receiverId, List<DeliveryCondition> conditions) {
        return new ReceiverDeliveryConditionResponse(
                receiverId,
                conditions.stream().map(DeliveryConditionItemResponse::from).toList()
        );
    }
}
