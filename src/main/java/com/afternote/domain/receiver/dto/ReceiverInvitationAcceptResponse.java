package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReceiverInvitationAcceptResponse(
        @Schema(description = "등록된 수신자 ID", example = "12")
        Long receiverId,

        @Schema(description = "초대한 사용자 이름", example = "김수아")
        String inviterName
) {
}
