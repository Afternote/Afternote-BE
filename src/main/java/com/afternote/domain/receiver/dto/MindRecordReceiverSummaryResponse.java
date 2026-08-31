package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.Receiver;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
@Schema(description = "마인드레코드 수신자 요약")
public record MindRecordReceiverSummaryResponse(
        @Schema(description = "수신자 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long receiverId,

        @Schema(description = "수신자 이름", example = "박채연", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String name
) {



    public static MindRecordReceiverSummaryResponse from(Receiver receiver) {
        return MindRecordReceiverSummaryResponse.builder()
                .receiverId(receiver.getId())
                .name(receiver.getName())
                .build();
    }
}
