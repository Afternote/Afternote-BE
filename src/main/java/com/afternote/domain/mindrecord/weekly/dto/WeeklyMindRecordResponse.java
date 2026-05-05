package com.afternote.domain.mindrecord.weekly.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "주간 마음기록 조회 응답")
public class WeeklyMindRecordResponse {

    @Schema(description = "해당 주 데일리 질문(답변 완료) 수")
    private int dailyQuestionAmount;

    @Schema(description = "해당 주 일기(비임시) 수")
    private int diaryAmount;

    @Schema(description = "해당 주 깊은 생각(비임시) 수")
    private int deepThoughtAmount;

    @Schema(description = "주간 인사이트 요약 (Gemini)")
    private String summaryText;

    @Schema(description = "해당 주 기록 타임라인")
    private List<WeekRecordItem> week;

    @JsonProperty("daily-question")
    @Schema(description = "해당 주 데일리 질문 답변 목록")
    private List<WeeklyDailyQuestionItem> dailyQuestion;

    @Schema(description = "해당 주 상위 감정 키워드 (최대 3)")
    private List<WeeklyEmotionItem> emotions;

    @Getter
    @Builder
    public static class WeekRecordItem {
        private Long diaryId;
        private int day;
        private boolean isDiary;
        private String emotion;
    }

    @Getter
    @Builder
    public static class WeeklyDailyQuestionItem {
        private String title;

        @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
        private String content;

        private String date;
    }

    @Getter
    @Builder
    public static class WeeklyEmotionItem {
        private String keyword;
        private int percentage;
    }
}
