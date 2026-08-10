package com.afternote.domain.mindrecord.weekly.dto;

import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import com.fasterxml.jackson.annotation.JsonProperty;
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

        @Schema(description = "해당 주 캘린더(day당 1개). 일기 우선, 같은 날 일기 여러 개면 최신 todayMood")
        @Getter
        List<WeekRecordItem> week,

        @JsonProperty("daily-question")
        @Schema(description = "해당 주 데일리 질문 답변 목록")
        @Getter
        List<WeeklyDailyQuestionItem> dailyQuestion,

        @Schema(description = "해당 주 상위 감정 키워드 (최대 3, 분석 성공분만)")
        @Getter
        List<WeeklyEmotionItem> emotions,

        @Schema(description = "해당 주 감정 분석 진행 상태 요약")
        @Getter
        EmotionAnalysisSummary emotionAnalysis
) {

    @Builder
    public static record EmotionAnalysisSummary(
            @Schema(description = "분석 대상 기록 수")
            @Getter
            int total,

            @Schema(description = "분석 성공 수")
            @Getter
            int succeeded,

            @Schema(description = "분석 대기/재시도 중 수")
            @Getter
            int pending,

            @Schema(description = "분석 실패(재시도 소진) 수")
            @Getter
            int failed
    ) {
    }

    @Builder
    public static record WeekRecordItem(
            @Schema(
                    description = "원본 기록 ID (맵/리스트 키). type에 따라 일기/데일리질문/깊은생각 PK",
                    example = "27",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @Getter
            Long diaryId,

            @Schema(description = "일(day of month)", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
            @Getter
            int day,

            @Schema(
                    description = "기록 타입. DIARY=일기, DAILY_QUESTION=데일리질문, DEEP_THOUGHT=깊은생각",
                    example = "DIARY",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @Getter
            WeekRecordType type,

            @Schema(
                    description = "주간 캘린더 이모지용 일기 todayMood (HAPPY/SOSO/SAD). "
                            + "type=DIARY일 때만 채우고, Gemini 감정분석 결과는 emotions[]에만 둔다. "
                            + "DIARY가 아니면 null(점 표시).",
                    example = "HAPPY",
                    allowableValues = {"HAPPY", "SOSO", "SAD"},
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
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
