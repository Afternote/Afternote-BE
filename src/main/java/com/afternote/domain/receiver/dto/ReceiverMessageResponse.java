package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "수신자 메시지 조회 응답")
public record ReceiverMessageResponse(
        @Schema(description = "발신자 이름", example = "김철수", requiredMode = Schema.RequiredMode.REQUIRED)
        String senderName,
        @Schema(
                description = "메시지 내용. 발신자가 남긴 메시지가 없으면 null",
                example = "사랑하는 딸에게...",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String message,
        @Schema(
                description = "메시지 작성일시. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)",
                example = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        LocalDateTime createdAt
) {
}
