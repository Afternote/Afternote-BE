package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.dto.ReceiverInvitationAcceptResponse;
import com.afternote.domain.receiver.dto.ReceiverInvitationCreateResponse;
import com.afternote.domain.receiver.dto.ReceiverInvitationResponse;
import com.afternote.domain.receiver.service.ReceiverInvitationService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Receiver Invitation API", description = "카카오톡 공유용 수신자 초대 API")
@RestController
@RequestMapping("/api/v1/receiver-invitations")
@RequiredArgsConstructor
public class ReceiverInvitationController {

    private final ReceiverInvitationService receiverInvitationService;

    @Operation(
            summary = "수신자 초대 링크 생성",
            description = "로그인한 사용자가 카카오톡으로 공유할 일회성 수신자 초대 링크를 생성합니다."
    )
    @PostMapping
    public ApiResponse<ReceiverInvitationCreateResponse> createInvitation(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(receiverInvitationService.createInvitation(userId));
    }

    @Operation(
            summary = "수신자 초대 정보 조회",
            description = "로그인 전에도 초대한 사용자와 초대 상태 및 만료 여부를 확인할 수 있습니다."
    )
    @GetMapping("/{token}")
    public ApiResponse<ReceiverInvitationResponse> getInvitation(@PathVariable String token) {
        return ApiResponse.success(receiverInvitationService.getInvitation(token));
    }

    @Operation(
            summary = "수신자 초대 수락",
            description = "로그인한 사용자가 초대를 수락합니다. 관계와 메시지는 null로 수신자가 즉시 등록됩니다. 동일 사용자의 재요청은 멱등 처리됩니다."
    )
    @PostMapping("/{token}/accept")
    public ApiResponse<ReceiverInvitationAcceptResponse> acceptInvitation(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable String token
    ) {
        return ApiResponse.success(receiverInvitationService.acceptInvitation(userId, token));
    }
}
