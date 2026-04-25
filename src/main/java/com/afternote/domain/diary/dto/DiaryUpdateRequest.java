package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.TodayMood;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "다이어리 수정 요청")
@Getter
@NoArgsConstructor
public class DiaryUpdateRequest {

    @Schema(description = "제목", example = "수정된 제목")
    private String title;

    @Schema(description = "내용", example = "수정된 내용")
    private String content;

    @Schema(description = "임시저장 여부", example = "true")
    private Boolean isDraft;

    @Schema(description = "이미지 URL", example = "https://example.com/updated-image.jpg")
    private String imageUrl;

    @Schema(description = "오늘의 기분", example = "SOSO")
    private TodayMood todayMood;
}
