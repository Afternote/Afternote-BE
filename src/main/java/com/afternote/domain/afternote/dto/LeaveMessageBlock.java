package com.afternote.domain.afternote.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
public record LeaveMessageBlock(
        @Schema(description = "남기실 말씀 제목 (선택)", example = "남긴말1")
        @Getter
        String title,

        @Schema(description = "남기실 말씀 본문", example = "언제나 고맙고 사랑한다.")
        @Getter
        String body
) {
}
