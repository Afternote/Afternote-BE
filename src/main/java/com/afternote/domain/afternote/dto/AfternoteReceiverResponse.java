package com.afternote.domain.afternote.dto;

import com.afternote.domain.receiver.model.Receiver;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
@Schema(description = "애프터노트 수신자 요약")
public record AfternoteReceiverResponse(
        @Schema(description = "수신자 ID", example = "1")
        @Getter
        Long receiverId,

        @Schema(description = "수신자 이름", example = "김소희")
        @Getter
        String name,

        @Schema(description = "수신인과의 관계", example = "딸")
        @Getter
        String relation
) {

    public static AfternoteReceiverResponse from(Receiver receiver) {
        return AfternoteReceiverResponse.builder()
                .receiverId(receiver.getId())
                .name(receiver.getName())
                .relation(receiver.getRelation())
                .build();
    }
}
