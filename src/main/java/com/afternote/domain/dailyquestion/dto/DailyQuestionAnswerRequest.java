package com.afternote.domain.dailyquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DailyQuestionAnswerRequest {

    @Schema(description = "유저 데일리 질문(답변) ID", example = "3")
    @NotNull(message = "질문 ID는 필수입니다.")
    private Long questionId;

    @Schema(description = "답변 내용", example = "오늘 날씨가 좋아서 산책을 다녀왔습니다.")
    @NotBlank(message = "답변 내용은 필수입니다.")
    private String content;

    @Schema(description = "이미지 URL", example = "https://s3.../image.jpg", nullable = true)
    private String imageUrl;

    @Schema(description = "임시저장 여부", example = "false")
    private Boolean isDraft;
}