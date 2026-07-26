package com.afternote.domain.mindrecord.weekly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
@Schema(description = "주간 마음기록 조회 응답")
public record WeeklyMindRecordResponse(
        @Schema(description = "해당 주 데일리 질문(답변 완료) 수")
        @Getter
        int dailyQuestionAmount,

        @Schema(description = "해당 주 일기(비임시) 수")
        @Getter
        int diaryAmount,

        @Schema(description = "해당 주 깊은 생각(비임시) 수")
        @Getter
        int deepThoughtAmount,

        @Schema(description = "주간 인사이트 요약 (Gemini)")
        @Getter
        String summaryText,

        @Schema(description = "해당 주 기록 타임라인")
        @Getter
        List<WeekRecordItem> week,

        @JsonProperty("daily-question")
        @Schema(description = "해당 주 데일리 질문 답변 목록")
        @Getter
        List<WeeklyDailyQuestionItem> dailyQuestion,

        @Schema(description = "해당 주 상위 감정 키워드 (최대 3)")
        @Getter
        List<WeeklyEmotionItem> emotions
) {








    @Builder
    public static record WeekRecordItem(
            @Getter
            Long diaryId,

            @Getter
            int day,

            boolean isDiary,

            @Getter
            String emotion
    ) {
    }

    @Builder
    public static record WeeklyDailyQuestionItem(
            @Getter
            String title,

            @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
            @Getter
            String content,

            @Getter
            String date
    ) {


    }

    @Builder
    public static record WeeklyEmotionItem(
            @Getter
            String keyword,

            @Getter
            int percentage
    ) {
    }
}
