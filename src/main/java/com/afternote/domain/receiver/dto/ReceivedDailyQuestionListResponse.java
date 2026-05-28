package com.afternote.domain.receiver.dto;

import com.afternote.domain.dailyquestion.dto.DailyQuestionListResponse;

import java.util.List;

public record ReceivedDailyQuestionListResponse(
        List<DailyQuestionListResponse> dailyQuestions
) {
    public static ReceivedDailyQuestionListResponse from(List<DailyQuestionListResponse> dailyQuestions) {
        return new ReceivedDailyQuestionListResponse(dailyQuestions);
    }
}