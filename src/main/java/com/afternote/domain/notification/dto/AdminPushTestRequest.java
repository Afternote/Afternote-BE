package com.afternote.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

public record AdminPushTestRequest(
        @Schema(description = "푸시를 받을 사용자 ID. 생략하면 관리자 본인", example = "1")
        Long userId,

        @Schema(description = "알림 제목", example = "AfterNote")
        String title,

        @Schema(description = "알림 본문", example = "서버에서 보낸 테스트 푸시입니다.")
        String body,

        @Schema(description = "테스트할 화면 이동 데이터. 모든 값은 문자열. 잘못된 목적지·필수 ID 누락·action은 400/1400",
                example = "{\"destination\":\"TIME_LETTER_DETAIL\",\"timeLetterId\":\"123\",\"action\":\"EDIT\"}")
        Map<String, String> data
) {
    public AdminPushTestRequest(Long userId, String title, String body) { this(userId, title, body, null); }
}
