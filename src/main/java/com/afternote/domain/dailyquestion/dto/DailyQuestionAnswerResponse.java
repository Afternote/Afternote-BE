package com.afternote.domain.dailyquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyQuestionAnswerResponse {
    @Schema(description = "유저 데일리 질문(답변) ID", example = "12")
    private Long userDailyQuestionId;

    @Schema(description = "답변 내용", example = "오늘 날씨가 좋아서 산책을 다녀왔습니다.")
    private String content;

    @Schema(description = "이미지 URL", example = "https://s3.../image.jpg", nullable = true)
    private String imageUrl;

    @Schema(description = "임시저장 여부", example = "false")
    private boolean isDraft;
}