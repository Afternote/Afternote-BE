package com.afternote.domain.receiver.dto;

import com.afternote.domain.deepthought.dto.DeepThoughtResponse;
import com.afternote.domain.deepthought.dto.DeepThoughtTagCountResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "수신한 깊은 생각 목록 응답")
public record ReceivedDeepThoughtListResponse(
        @Schema(description = "카테고리 목록. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> categories,
        @Schema(description = "태그별 개수. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DeepThoughtTagCountResponse> tagCounts,
        @Schema(description = "깊은 생각 목록. 없으면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DeepThoughtResponse> deepThoughts
) {
    public static ReceivedDeepThoughtListResponse from(
            List<String> categories,
            List<DeepThoughtTagCountResponse> tagCounts,
            List<DeepThoughtResponse> deepThoughts
    ) {
        return new ReceivedDeepThoughtListResponse(categories, tagCounts, deepThoughts);
    }
}
