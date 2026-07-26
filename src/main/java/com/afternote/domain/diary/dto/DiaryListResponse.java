package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.TodayMood;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Schema(description = "다이어리 월 단위 목록 응답")
@Builder
public record DiaryListResponse(
        @Schema(description = "조회 대상 연-월 (yyyy-MM)", example = "2026-03")
        @Getter
        String yearMonth,

        @Schema(description = "조회 대상 달의 다이어리 목록 (createdAt 내림차순)")
        @Getter
        List<DiaryResponse> diaries,

        @Schema(description = "조회 대상 달의 정식 등록(비임시) 다이어리 개수", example = "18")
        @Getter
        long monthDiaryCount,

        @Schema(description = "최근 7일(서버 기준 오늘 포함) 정식 다이어리 중 가장 많이 기록된 기분. 해당 구간에 기록이 없으면 null")
        @Getter
        TodayMood weeklyDominantMood
) {

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");





    public static DiaryListResponse from(
            YearMonth yearMonth,
            List<DiaryResponse> diaries,
            long monthDiaryCount,
            TodayMood weeklyDominantMood
    ) {
        return DiaryListResponse.builder()
                .yearMonth(yearMonth.format(YEAR_MONTH_FORMATTER))
                .diaries(diaries)
                .monthDiaryCount(monthDiaryCount)
                .weeklyDominantMood(weeklyDominantMood)
                .build();
    }
}
