package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "받은 기록함 리스트 응답")
public record ReceivedRecordBoxListResponse(

        @Schema(description = "받은 기록함 목록. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ReceivedRecordBoxResponse> recordBoxes
) {
    public static ReceivedRecordBoxListResponse from(List<ReceivedRecordBoxResponse> recordBoxes) {
        return new ReceivedRecordBoxListResponse(recordBoxes);
    }
}
