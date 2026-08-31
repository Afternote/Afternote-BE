package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.Receiver;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "수신자 인증번호 검증 응답")
public record ReceiverAuthVerifyResponse(
        @Schema(description = "수신자 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long receiverId,
        @Schema(description = "수신자 이름", example = "김지은", requiredMode = Schema.RequiredMode.REQUIRED)
        String receiverName,
        @Schema(description = "발신자 이름", example = "김철수", requiredMode = Schema.RequiredMode.REQUIRED)
        String senderName,
        @Schema(
                description = ReceivedRecordBoxResponse.RELATION_DESCRIPTION,
                example = "딸",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String relation
) {
    public static ReceiverAuthVerifyResponse from(Receiver receiver, String senderName) {
        return new ReceiverAuthVerifyResponse(
                receiver.getId(),
                receiver.getName(),
                senderName,
                receiver.getRelation()
        );
    }
}
