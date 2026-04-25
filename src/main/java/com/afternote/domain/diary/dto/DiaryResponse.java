package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Schema(description = "다이어리 응답")
@Getter
@Builder
public class DiaryResponse {
    private static final DateTimeFormatter KOREAN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN);

    @Schema(description = "다이어리 ID", example = "1")
    private Long diaryId;

    @Schema(description = "제목")
    private String title;

    @Schema(description = "내용")
    private String content;

    @Schema(description = "임시저장 여부")
    private Boolean isDraft;

    @Schema(description = "이미지 URL")
    private String imageUrl;

    @Schema(description = "감정")
    private String emotion;

    @Schema(description = "오늘의 기분")
    private TodayMood todayMood;

    @Schema(description = "생성일 (yyyy.MM.dd E)", example = "2026.04.25 토")
    private String createdAt;

    @Schema(description = "수정일 (yyyy.MM.dd E)", example = "2026.04.25 토")
    private String updatedAt;

    public static DiaryResponse from(Diary diary) {
        return DiaryResponse.builder()
                .diaryId(diary.getId())
                .title(diary.getTitle())
                .content(diary.getContent())
                .isDraft(diary.getIsDraft())
                .imageUrl(diary.getImageUrl())
                .todayMood(diary.getTodayMood())
                .createdAt(formatDate(diary.getCreatedAt()))
                .updatedAt(formatDate(diary.getUpdatedAt()))
                .build();
    }
    
    public static DiaryResponse from(Diary diary, String emotion, TodayMood todayMood) {
        return DiaryResponse.builder()
                .diaryId(diary.getId())
                .title(diary.getTitle())
                .content(diary.getContent())
                .isDraft(diary.getIsDraft())
                .imageUrl(diary.getImageUrl())
                .emotion(emotion)
                .todayMood(todayMood)
                .createdAt(formatDate(diary.getCreatedAt()))
                .updatedAt(formatDate(diary.getUpdatedAt()))
                .build();
    }

    private static String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(KOREAN_DATE_FORMATTER) : null;
    }
}
