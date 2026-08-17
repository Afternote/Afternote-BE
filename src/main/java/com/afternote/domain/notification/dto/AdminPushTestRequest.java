package com.afternote.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPushTestRequest(
        @Schema(description = "푸시를 받을 사용자 ID. 생략하면 관리자 본인", example = "1")
        Long userId,

        @Schema(description = "알림 제목", example = "AfterNote")
        String title,

        @Schema(description = "알림 본문", example = "서버에서 보낸 테스트 푸시입니다.")
        String body
) {
}
