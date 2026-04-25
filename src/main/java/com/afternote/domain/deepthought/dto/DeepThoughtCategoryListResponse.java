package com.afternote.domain.deepthought.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@Schema(description = "깊은 생각 카테고리 목록 응답")
public class DeepThoughtCategoryListResponse {

    @Schema(description = "카테고리 목록")
    private List<DeepThoughtCategoryResponse> categories;

    public static DeepThoughtCategoryListResponse from(List<DeepThoughtCategoryResponse> categories) {
        return DeepThoughtCategoryListResponse.builder()
                .categories(categories)
                .build();
    }
}
