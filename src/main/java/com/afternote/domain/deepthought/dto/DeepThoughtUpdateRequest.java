package com.afternote.domain.deepthought.dto;

import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "깊은 생각 수정 요청")
public class DeepThoughtUpdateRequest {

    @Schema(description = "제목", example = "수정된 제목")
    private String title;

    @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
    private String content;

    @Schema(description = "임시저장 여부", example = "true")
    private Boolean isDraft;

    @Schema(description = "카테고리", example = "나의 가치관")
    private String category;

    @Schema(description = "태그 목록. null이면 변경하지 않음, 빈 배열이면 전체 해제", example = "[\"성장\", \"회고\"]")
    private List<String> tags;

    @Schema(description = "수신자 ID 목록. null이면 변경하지 않음, 빈 배열이면 전체 해제", example = "[1, 2]")
    private List<Long> receiverIds;
}
