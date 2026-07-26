package com.afternote.domain.deepthought.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Schema(description = "깊은 생각 카테고리 생성 요청")
public record DeepThoughtCategoryCreateRequest(
        @Schema(description = "카테고리명", example = "성장")
        @NotBlank(message = "카테고리명은 필수입니다.")
        @Getter
        String title
) {
}
