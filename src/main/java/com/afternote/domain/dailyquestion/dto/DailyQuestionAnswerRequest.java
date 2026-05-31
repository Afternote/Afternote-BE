package com.afternote.domain.dailyquestion.dto;

import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class DailyQuestionAnswerRequest {

    @Schema(description = "유저 데일리 질문(답변) ID", example = "3")
    @NotNull(message = "질문 ID는 필수입니다.")
    private Long questionId;

    @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
    @NotBlank(message = "답변 내용은 필수입니다.")
    private String content;

    @Schema(description = "임시저장 여부", example = "false")
    private Boolean isDraft;

    @Schema(description = "수신자 ID 목록", example = "[1, 2]")
    private List<Long> receiverIds;
}