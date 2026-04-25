package com.afternote.domain.dailyquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DailyQuestionUpdateRequest {

    @Schema(description = "수정할 답변 내용", example = "수정된 답변입니다.")
    private String content;

    @Schema(description = "수정할 이미지 URL", example = "https://s3.../new-image.jpg", nullable = true)
    private String imageUrl;
    
    @Schema(description = "임시저장 여부", example = "false")
    private Boolean isDraft;
}