package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.TodayMood;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "다이어리 목록 응답")
@Getter
@Builder
public class DiaryListResponse {

    @Schema(description = "다이어리 목록")
    private List<DiaryResponse> diaries;

    @Schema(description = "이번 달 정식 등록(비임시) 다이어리 개수", example = "18")
    private long thisMonthDiaryCount;

    @Schema(description = "최근 7일(오늘 포함) 정식 다이어리 중 가장 많이 기록된 기분. 해당 구간에 기록이 없으면 null")
    private TodayMood weeklyDominantMood;

    public static DiaryListResponse from(List<DiaryResponse> diaries) {
        return from(diaries, 0L, null);
    }

    public static DiaryListResponse from(
            List<DiaryResponse> diaries,
            long thisMonthDiaryCount,
            TodayMood weeklyDominantMood
    ) {
        return DiaryListResponse.builder()
                .diaries(diaries)
                .thisMonthDiaryCount(thisMonthDiaryCount)
                .weeklyDominantMood(weeklyDominantMood)
                .build();
    }
}
