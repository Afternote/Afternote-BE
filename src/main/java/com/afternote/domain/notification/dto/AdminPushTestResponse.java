package com.afternote.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record AdminPushTestResponse(
        @Schema(description = "푸시를 보낸 사용자 ID")
        Long targetUserId,

        @Schema(description = "발송 시도한 기기 토큰 수")
        int tokenCount
) {
}
