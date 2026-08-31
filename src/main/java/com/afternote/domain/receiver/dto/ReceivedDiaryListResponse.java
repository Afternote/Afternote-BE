package com.afternote.domain.receiver.dto;

import com.afternote.domain.diary.dto.DiaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "수신한 일기 목록 응답")
public record ReceivedDiaryListResponse(
        @Schema(description = "일기 목록. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DiaryResponse> diaries
) {
    public static ReceivedDiaryListResponse from(List<DiaryResponse> diaries) {
        return new ReceivedDiaryListResponse(diaries);
    }
}
