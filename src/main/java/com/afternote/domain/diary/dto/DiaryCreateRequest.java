package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.TodayMood;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "다이어리 생성 요청")
@Getter
@NoArgsConstructor
public class DiaryCreateRequest {

    @Schema(description = "제목", example = "오늘의 일기")
    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @Schema(description = "내용", example = "오늘은 좋은 하루였다.")
    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    @Schema(description = "임시저장 여부", example = "false")
    @NotNull(message = "isDraft 값은 필수입니다.")
    private Boolean isDraft;

    @Schema(description = "이미지 URL", example = "https://example.com/image.jpg")
    private String imageUrl;

    @Schema(description = "오늘의 기분", example = "HAPPY")
    private TodayMood todayMood;
}
