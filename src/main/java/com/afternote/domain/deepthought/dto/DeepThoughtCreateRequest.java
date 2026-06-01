package com.afternote.domain.deepthought.dto;

import com.afternote.global.sanitizer.MindRecordHtmlSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@Schema(description = "깊은 생각 작성 요청")
public class DeepThoughtCreateRequest {

    @Schema(description = "제목", example = "나의 성장에 대한 생각")
    @NotBlank(message = "제목은 필수입니다.")
    private String title;

    @Schema(description = MindRecordHtmlSchema.CONTENT, example = MindRecordHtmlSchema.CONTENT_EXAMPLE)
    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    @Schema(description = "임시저장 여부", example = "false")
    @NotNull(message = "임시저장 여부는 필수입니다.")
    private Boolean isDraft;

    @Schema(description = "카테고리", example = "나의 가치관")
    @NotBlank(message = "카테고리는 필수입니다.")
    private String category;

    @Schema(description = "태그 목록", example = "[\"성장\", \"회고\"]")
    private List<String> tags;

    @Schema(description = "수신자 ID 목록", example = "[1, 2]")
    private List<Long> receiverIds;
}
