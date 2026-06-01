package com.afternote.domain.dailyquestion.dto;

import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class DailyQuestionUpdateRequest {

    @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
    private String content;

    @Schema(description = "임시저장 여부", example = "false")
    private Boolean isDraft;

    @Schema(description = "수신자 ID 목록. null이면 변경하지 않음, 빈 배열이면 전체 해제", example = "[1, 2]")
    private List<Long> receiverIds;
}