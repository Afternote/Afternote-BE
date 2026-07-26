package com.afternote.domain.dailyquestion.dto;

import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

public record DailyQuestionUpdateRequest(
        @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
        @Getter
        String content,

        @Schema(description = "임시저장 여부", example = "false")
        @Getter
        Boolean isDraft,

        @Schema(description = "수신자 ID 목록. null이면 변경하지 않음, 빈 배열이면 전체 해제", example = "[1, 2]")
        @Getter
        List<Long> receiverIds
) {



}