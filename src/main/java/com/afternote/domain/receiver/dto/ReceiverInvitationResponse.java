package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.ReceiverInvitation;
import com.afternote.domain.receiver.model.ReceiverInvitationStatus;
import com.afternote.domain.user.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ReceiverInvitationResponse(
        @Schema(description = "초대한 사용자 이름", example = "김수아")
        String inviterName,

        @Schema(description = "초대 상태", example = "PENDING")
        ReceiverInvitationStatus status,

        @Schema(description = "초대 만료 여부")
        boolean expired,

        @Schema(description = "초대 만료 시각")
        LocalDateTime expiresAt
) {
    public static ReceiverInvitationResponse from(
            ReceiverInvitation invitation,
            User inviter,
            LocalDateTime now
    ) {
        return new ReceiverInvitationResponse(
                inviter.getName(),
                invitation.getStatus(),
                invitation.isExpired(now),
                invitation.getExpiresAt()
        );
    }
}
