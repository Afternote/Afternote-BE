package com.afternote.domain.user.dto;

import com.afternote.domain.receiver.model.Receiver;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
public record ReceiverListResponse(
        @Schema(description = "수신인 ID", example = "1")
        @Getter
        Long receiverId,

        @Schema(description = "수신인 이름", example = "김소희")
        @Getter
        String name,

        @Schema(description = "수신인과의 관계", example = "딸")
        @Getter
        String relation
) {





    public static ReceiverListResponse from(Receiver receiver) {
        return ReceiverListResponse.builder()
                .receiverId(receiver.getId())
                .name(receiver.getName())
                .relation(receiver.getRelation())
                .build();
    }
}
