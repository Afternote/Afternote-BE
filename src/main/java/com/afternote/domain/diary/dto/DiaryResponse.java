package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.Diary;
import com.afternote.domain.diary.model.TodayMood;
import com.afternote.domain.receiver.dto.MindRecordReceiverSummaryResponse;
import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Schema(description = "다이어리 응답")
@Builder
public record DiaryResponse(
        @Schema(description = "다이어리 ID", example = "1")
        @Getter
        Long diaryId,

        @Schema(description = "제목")
        @Getter
        String title,

        @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
        @Getter
        String content,

        @Schema(description = "임시저장 여부")
        @Getter
        Boolean isDraft,

        @Schema(description = "감정")
        @Getter
        String emotion,

        @Schema(description = "오늘의 기분")
        @Getter
        TodayMood todayMood,

        @Schema(description = "작성일 (ISO yyyy-MM-dd). 캘린더 셀 그루핑 / 날짜 필터링에 사용", example = "2026-04-25")
        @Getter
        LocalDate date,

        @Schema(description = "생성일 (yyyy.MM.dd E)", example = "2026.04.25 토")
        @Getter
        String createdAt,

        @Schema(description = "수정일 (yyyy.MM.dd E)", example = "2026.04.25 토")
        @Getter
        String updatedAt,

        @Schema(description = "수신자 목록")
        @Getter
        List<MindRecordReceiverSummaryResponse> receivers
) {
    private static final DateTimeFormatter KOREAN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy.MM.dd E", Locale.KOREAN);











    public static DiaryResponse from(Diary diary) {
        return from(diary, List.of());
    }

    public static DiaryResponse from(Diary diary, List<MindRecordReceiverSummaryResponse> receivers) {
        return DiaryResponse.builder()
                .diaryId(diary.getId())
                .title(diary.getTitle())
                .content(diary.getContent())
                .isDraft(diary.getIsDraft())
                .todayMood(diary.getTodayMood())
                .date(toLocalDate(diary.getCreatedAt()))
                .createdAt(formatDate(diary.getCreatedAt()))
                .updatedAt(formatDate(diary.getUpdatedAt()))
                .receivers(receivers != null ? receivers : List.of())
                .build();
    }
    
    public static DiaryResponse from(Diary diary, String emotion, TodayMood todayMood) {
        return from(diary, emotion, todayMood, List.of());
    }

    public static DiaryResponse from(
            Diary diary,
            String emotion,
            TodayMood todayMood,
            List<MindRecordReceiverSummaryResponse> receivers
    ) {
        return DiaryResponse.builder()
                .diaryId(diary.getId())
                .title(diary.getTitle())
                .content(diary.getContent())
                .isDraft(diary.getIsDraft())
                .emotion(emotion)
                .todayMood(todayMood)
                .date(toLocalDate(diary.getCreatedAt()))
                .createdAt(formatDate(diary.getCreatedAt()))
                .updatedAt(formatDate(diary.getUpdatedAt()))
                .receivers(receivers != null ? receivers : List.of())
                .build();
    }

    private static LocalDate toLocalDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalDate() : null;
    }

    private static String formatDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(KOREAN_DATE_FORMATTER) : null;
    }
}
