package com.afternote.domain.deepthought.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Builder
@Schema(description = "깊은 생각 목록 응답")
public record DeepThoughtListResponse(
        @Schema(description = "깊은 생각 목록")
        @Getter
        List<DeepThoughtResponse> deepThoughts,

        @Schema(description = "태그별 글 개수. 목록과 동일한 날짜·카테고리·draftOnly 조건이며, 태그 검색어(tag)는 적용하지 않음")
        @Getter
        List<DeepThoughtTagCountResponse> tagCounts
) {



    public static DeepThoughtListResponse from(List<DeepThoughtResponse> deepThoughts) {
        return from(deepThoughts, Collections.emptyList());
    }

    public static DeepThoughtListResponse from(
            List<DeepThoughtResponse> deepThoughts,
            List<DeepThoughtTagCountResponse> tagCounts
    ) {
        return DeepThoughtListResponse.builder()
                .deepThoughts(deepThoughts)
                .tagCounts(tagCounts)
                .build();
    }
}
