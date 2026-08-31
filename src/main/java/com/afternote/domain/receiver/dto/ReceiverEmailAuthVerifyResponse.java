package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.Receiver;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Schema(description = "수신자 이메일 인증번호 검증 응답")
public record ReceiverEmailAuthVerifyResponse(
        @Schema(description = "수신자 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long receiverId,

        @Schema(description = "수신자 이름", example = "김지은", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String receiverName,

        @Schema(description = "발신자 이름", example = "김철수", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String senderName,

        @Schema(
                description = "이후 X-Auth-Code 헤더에 넣을 UUID 접근 코드",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        String accessCode
) {
    public static ReceiverEmailAuthVerifyResponse from(Receiver receiver, String senderName) {
        return new ReceiverEmailAuthVerifyResponse(
                receiver.getId(),
                receiver.getName(),
                senderName,
                receiver.getAuthCode()
        );
    }
}
