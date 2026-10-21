package com.afternote.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
public record AdminPushTestResponse(
        @Schema(description = "테스트 발송 대상 사용자 ID")
        Long targetUserId,

        @Schema(description = "대상 사용자에게 등록된 토큰 수. 무효·권한 거부 토큰도 포함하며 실제 발송·성공 수가 아님")
        int tokenCount
) {
}
