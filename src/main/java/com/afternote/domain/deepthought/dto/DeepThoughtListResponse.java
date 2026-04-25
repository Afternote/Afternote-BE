package com.afternote.domain.deepthought.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "깊은 생각 목록 응답")
public class DeepThoughtListResponse {

    @Schema(description = "깊은 생각 목록")
    private List<DeepThoughtResponse> deepThoughts;

    public static DeepThoughtListResponse from(List<DeepThoughtResponse> deepThoughts) {
        return DeepThoughtListResponse.builder()
                .deepThoughts(deepThoughts)
                .build();
    }
}
