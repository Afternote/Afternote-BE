package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.TodayMood;
import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "다이어리 수정 요청")
public record DiaryUpdateRequest(
        @Schema(
                description = "제목. 생략 시 유지. 정식 등록(isDraft=false) 전환 시 필수",
                example = "수정된 제목",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        String title,

        @Schema(
                description = MindRecordHtmlSchema.CONTENT + " 생략 시 유지. 정식 등록 전환 시 필수",
                example = MindRecordHtmlSchema.CONTENT_EXAMPLE,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        String content,

        @Schema(description = "임시저장 여부", example = "true")
        @Getter
        Boolean isDraft,

        @Schema(
                description = "오늘의 기분. 생략 시 유지. 정식 등록(isDraft=false) 전환 시 필수",
                example = "SOSO",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        TodayMood todayMood,

        @Schema(
                description = "기록일 (yyyy-MM-dd). 생략 시 유지. 미래 날짜는 거부(code 2101). 과거는 제한 없음",
                example = "2026-08-01"
        )
        @Getter
        LocalDate date,

        @Schema(description = "수신자 ID 목록. null이면 변경하지 않음, 빈 배열이면 전체 해제", example = "[1, 2]")
        @Getter
        List<Long> receiverIds
) {
}
