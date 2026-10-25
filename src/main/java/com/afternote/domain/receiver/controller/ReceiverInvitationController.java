package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.dto.OwnedInvitationResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@Tag(name = "Receiver Invitation API", description = "카카오톡 공유용 수신자 초대 API. 링크 공유와 자동 알림톡 전송은 별개입니다.")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "로그인 필요 (공개 초대 조회 제외)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "초대·회원·본인 수신자를 찾을 수 없음")
})
@RestController
@RequestMapping("/api/v1/receiver-invitations")
@RequiredArgsConstructor
public class ReceiverInvitationController {

    private final ReceiverInvitationService receiverInvitationService;

    @Operation(
            summary = "수신자 초대 링크 생성",
            description = "로그인한 사용자가 카카오톡으로 공유할 일회성 수신자 초대 링크를 생성합니다. 기본 만료는 14일이며 이미 발급된 링크의 만료 시각은 변경하지 않습니다."
    )
    @PostMapping
    public ApiResponse<ReceiverInvitationCreateResponse> createInvitation(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(receiverInvitationService.createInvitation(userId));
    }

    @Operation(summary = "기존 수신자 지정 초대 링크 생성", description = "본인 수신자에 연결되는 초대입니다. 자동 알림톡 전송은 별도 공급자 연동이 필요합니다.")
    @PostMapping("/receivers/{receiverId}")
    public ApiResponse<ReceiverInvitationCreateResponse> createTargetedInvitation(
            @Parameter(hidden = true) @UserId Long userId, @PathVariable Long receiverId) {
        return ApiResponse.success(receiverInvitationService.createTargetedInvitation(userId, receiverId));
    }

    @Operation(summary = "발신자용 초대 상세", description = "본인이 발급한 초대만 조회합니다. 공유용 토큰은 노출하지 않습니다.")
    @GetMapping("/owned/{invitationId}")
    public ApiResponse<OwnedInvitationResponse> getOwnedInvitation(
            @Parameter(hidden = true) @UserId Long userId, @PathVariable Long invitationId) {
        return ApiResponse.success(receiverInvitationService.getOwnedInvitation(userId, invitationId));
    }

    @Operation(summary = "수신자 초대 거절", description = "로그인이 필요합니다. 동일 사용자의 거절 재요청은 멱등 처리됩니다. 종료된 초대는 409, 만료된 대기 초대는 410입니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본인 초대 거절 불가, code=1908"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "종료된 초대, code=1910"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "만료된 대기 초대, code=1906")
    })
    @PostMapping("/{token}/decline")
    public ApiResponse<Void> declineInvitation(@Parameter(hidden = true) @UserId Long userId, @PathVariable String token) {
        receiverInvitationService.declineInvitation(userId, token);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "수신자 초대 정보 조회",
            description = "로그인 전에도 초대한 사용자와 초대 상태 및 만료 여부를 확인할 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @GetMapping("/{token}")
    public ApiResponse<ReceiverInvitationResponse> getInvitation(@PathVariable String token) {
        return ApiResponse.success(receiverInvitationService.getInvitation(token));
    }

    @Operation(
            summary = "수신자 초대 수락",
            description = "로그인한 사용자가 초대를 수락합니다. 공유 초대는 회원 정보로 새 수신자를 등록하며, 지정 초대는 기존 수신자의 연락처·관계·메시지를 보존하고 계정만 연결합니다. 동일 사용자의 재요청은 멱등 처리됩니다."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "본인 초대 수락 불가, code=1908"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 연결되었거나 종료된 초대, code=1907/1909/1910"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "만료된 초대, code=1906")
    })
    @PostMapping("/{token}/accept")
    public ApiResponse<ReceiverInvitationAcceptResponse> acceptInvitation(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable String token
    ) {
        return ApiResponse.success(receiverInvitationService.acceptInvitation(userId, token));
    }
}
