package com.afternote.domain.diary.dto;

import com.afternote.domain.diary.model.TodayMood;
import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "다이어리 수정 요청")
@Getter
@NoArgsConstructor
public class DiaryUpdateRequest {

    @Schema(description = "제목", example = "수정된 제목")
    private String title;

    @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
    private String content;

    @Schema(description = "임시저장 여부", example = "true")
    private Boolean isDraft;

    @Schema(description = "오늘의 기분", example = "SOSO")
    private TodayMood todayMood;

    @Schema(description = "수신자 ID 목록. null이면 변경하지 않음, 빈 배열이면 전체 해제", example = "[1, 2]")
    private List<Long> receiverIds;
}
