package com.afternote.domain.delivery.controller;

import com.afternote.domain.delivery.dto.ReceiverDeliveryConditionResponse;
import com.afternote.domain.delivery.dto.ReceiverDeliveryConditionUpdateRequest;
import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Delivery Condition API", description = "수신자별 사후 전달 조건 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/receivers/{receiverId}/delivery-conditions")
public class DeliveryConditionController {

    private final DeliveryConditionService deliveryConditionService;

    @Operation(
            summary = "수신자별 전달 조건 조회",
            description = "특정 수신자에 설정된 콘텐츠 타입별 사후 전달 조건을 조회합니다."
    )
    @GetMapping
    public ApiResponse<ReceiverDeliveryConditionResponse> getConditions(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(deliveryConditionService.getConditions(userId, receiverId));
    }

    @Operation(
            summary = "수신자별 전달 조건 설정/변경",
            description = """
                    특정 수신자에 콘텐츠 타입별 사후 전달 조건을 설정하거나 변경합니다.
                    - INACTIVITY: 미사용 기간(3/6/12개월) 필수
                    - RECEIVER_REQUEST: 수신자 서류 제출 후 운영자 승인
                    조건 변경 시 이미 제출된 서류/검증은 유지됩니다.
                    """
    )
    @PutMapping
    public ApiResponse<ReceiverDeliveryConditionResponse> updateConditions(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @Valid @RequestBody ReceiverDeliveryConditionUpdateRequest request
    ) {
        return ApiResponse.success(
                deliveryConditionService.updateConditions(userId, receiverId, request.getConditions())
        );
    }
}
