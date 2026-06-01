package com.afternote.domain.dailyquestion.dto;

import com.afternote.domain.receiver.dto.MindRecordReceiverSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DailyQuestionTodayResponse {
    @Schema(description = "유저 데일리 질문(답변) ID", example = "12")
    private Long questionId;

    @Schema(description = "몇 번째 질문인지 나타내는 day 값", example = "21")
    private Long day;

    @Schema(description = "질문 내용", example = "오늘 가장 감사했던 일은 무엇인가요?")
    private String content;

    @Schema(description = "답변 여부", example = "true")
    private boolean isAnswered;

    @Schema(description = "임시저장 여부", example = "false")
    private boolean isDraft;

    @Schema(description = "수신자 목록")
    private List<MindRecordReceiverSummaryResponse> receivers;
}