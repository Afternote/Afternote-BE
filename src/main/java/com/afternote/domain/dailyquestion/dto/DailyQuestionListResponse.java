package com.afternote.domain.dailyquestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DailyQuestionListResponse {

    @Schema(description = "유저 데일리 질문(답변) ID", example = "10")
    private Long userDailyQuestionId;

    @Schema(description = "질문 제목(내용)", example = "오늘 가장 감사했던 일은 무엇인가요?")
    private String title;

    @Schema(description = "답변 내용", example = "가족들과 함께 저녁을 먹은 것")
    private String content;

    @Schema(description = "생성일시 (yyyy.MM.dd E 포맷)", example = "2023.10.12 목")
    private String createdAt;

    @Schema(description = "이미지 URL", example = "https://s3.../image.jpg", nullable = true)
    private String imageUrl;
}