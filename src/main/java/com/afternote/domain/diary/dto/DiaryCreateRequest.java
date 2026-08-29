package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.TodayMood;
import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "다이어리 생성 요청")
public record DiaryCreateRequest(
        @Schema(description = "제목", example = "오늘의 일기")
        @NotBlank(message = "제목은 필수입니다.")
        @Getter
        String title,

        @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
        @NotBlank(message = "내용은 필수입니다.")
        @Getter
        String content,

        @Schema(description = "임시저장 여부", example = "false")
        @NotNull(message = "isDraft 값은 필수입니다.")
        @Getter
        Boolean isDraft,

        @Schema(description = "오늘의 기분 (필수)", example = "HAPPY", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "오늘의 기분은 필수입니다.")
        @Getter
        TodayMood todayMood,

        @Schema(
                description = "기록일 (yyyy-MM-dd). 미전송 시 오늘(Asia/Seoul). 미래 날짜는 거부(code 2101). 과거는 제한 없음",
                example = "2026-08-01"
        )
        @Getter
        LocalDate date,

        @Schema(description = "수신자 ID 목록", example = "[1, 2]")
        @Getter
        List<Long> receiverIds
) {
}
