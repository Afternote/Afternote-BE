package com.afternote.domain.deepthought.dto;

import com.afternote.domain.deepthought.model.DeepThought;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Getter
@Builder
@Schema(description = "깊은 생각 응답")
public class DeepThoughtResponse {
    private static final DateTimeFormatter KOREAN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN);

    @Schema(description = "깊은 생각 ID", example = "12")
    private Long deepThoughtId;

    @Schema(description = "제목", example = "나의 성장에 대한 생각")
    private String title;

    @Schema(description = "내용", example = "오늘은 스스로를 더 이해하는 하루였다.")
    private String content;

    @Schema(description = "임시저장 여부", example = "false")
    private Boolean isDraft;

    @Schema(description = "이미지 URL", nullable = true, example = "https://s3.../image.jpg")
    private String imageUrl;

    @Schema(description = "카테고리", example = "나의 가치관")
    private String category;

    @Schema(description = "태그 목록", example = "[\"성장\", \"회고\"]")
    private List<String> tags;

    @Schema(description = "생성일 (yyyy.MM.dd E)", example = "2026.04.25 토")
    private String createdAt;

    @Schema(description = "수정일 (yyyy.MM.dd E)", example = "2026.04.25 토")
    private String updatedAt;

    public static DeepThoughtResponse from(DeepThought deepThought) {
        return DeepThoughtResponse.builder()
                .deepThoughtId(deepThought.getId())
                .title(deepThought.getTitle())
                .content(deepThought.getContent())
                .isDraft(deepThought.getIsDraft())
                .imageUrl(deepThought.getImageUrl())
                .category(deepThought.getCategory())
                .tags(deepThought.getTags().stream()
                        .map(tag -> tag.getTitle())
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(tag -> !tag.isBlank())
                        .map(tag -> tag.startsWith("#") ? tag : "#" + tag)
                        .toList())
                .createdAt(formatDate(deepThought.getCreatedAt()))
                .updatedAt(formatDate(deepThought.getUpdatedAt()))
                .build();
    }

    private static String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(KOREAN_DATE_FORMATTER) : null;
    }
}
