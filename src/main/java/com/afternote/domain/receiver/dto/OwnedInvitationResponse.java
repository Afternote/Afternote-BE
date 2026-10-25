package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.ReceiverInvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "발신자 본인만 조회할 수 있는 초대 상태. 공유용 비밀 토큰은 반환하지 않습니다.")
public record OwnedInvitationResponse(Long invitationId,
        @Schema(nullable = true, description = "지정 초대 또는 수락 이후의 수신자 ID") Long receiverId,
        ReceiverInvitationStatus status, LocalDateTime expiresAt) { }
