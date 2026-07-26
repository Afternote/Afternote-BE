package com.afternote.domain.deepthought.dto;

import com.afternote.domain.deepthought.model.DeepThoughtCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
@Schema(description = "깊은 생각 카테고리 응답")
public record DeepThoughtCategoryResponse(
        @Schema(description = "카테고리 ID", example = "1")
        @Getter
        Long categoryId,

        @Schema(description = "카테고리명", example = "성장")
        @Getter
        String title
) {



    public static DeepThoughtCategoryResponse from(DeepThoughtCategory category) {
        return DeepThoughtCategoryResponse.builder()
                .categoryId(category.getId())
                .title(category.getTitle())
                .build();
    }
}
