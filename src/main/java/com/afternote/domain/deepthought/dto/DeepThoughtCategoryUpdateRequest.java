package com.afternote.domain.deepthought.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "깊은 생각 카테고리 수정 요청")
public class DeepThoughtCategoryUpdateRequest {

    @Schema(description = "수정할 카테고리명", example = "자기성찰")
    @NotBlank(message = "카테고리명은 필수입니다.")
    private String title;
}
