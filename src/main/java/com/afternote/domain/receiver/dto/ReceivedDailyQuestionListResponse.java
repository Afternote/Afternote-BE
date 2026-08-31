package com.afternote.domain.receiver.dto;

import com.afternote.domain.dailyquestion.dto.DailyQuestionListResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "수신한 매일의 질문 목록 응답")
public record ReceivedDailyQuestionListResponse(
        @Schema(description = "매일의 질문 목록. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DailyQuestionListResponse> dailyQuestions
) {
    public static ReceivedDailyQuestionListResponse from(List<DailyQuestionListResponse> dailyQuestions) {
        return new ReceivedDailyQuestionListResponse(dailyQuestions);
    }
}
