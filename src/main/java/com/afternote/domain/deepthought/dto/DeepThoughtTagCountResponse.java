package com.afternote.domain.deepthought.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Schema(description = "깊은 생각 태그별 개수")
public record DeepThoughtTagCountResponse(
        @Schema(description = "태그 이름", example = "성장")
        @Getter
        String tag,

        @Schema(description = "해당 태그가 붙은 깊은 생각 글 수(한 글에 같은 태그는 1회)", example = "3")
        @Getter
        long count
) {



    public DeepThoughtTagCountResponse(String tag, Long count) {
        this(tag, count == null ? 0L : count);
    }
}
