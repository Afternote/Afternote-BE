package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ReceiverInvitationCreateResponse(
        @Schema(description = "초대 ID", example = "1")
        Long invitationId,

        @Schema(description = "카카오 사용자 정의 템플릿의 inviteToken 인자에 전달할 일회성 토큰", example = "AbCDef123")
        String invitationToken,

        @Schema(description = "카카오톡으로 공유할 초대 URL", example = "https://afternote.kro.kr/invitations/abc123")
        String invitationUrl,

        @Schema(description = "초대 만료 시각")
        LocalDateTime expiresAt
) {
}
