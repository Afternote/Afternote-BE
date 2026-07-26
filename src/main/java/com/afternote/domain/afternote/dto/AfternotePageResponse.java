package com.afternote.domain.afternote.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Builder
public record AfternotePageResponse(
        @Schema(description = "애프터노트 목록")
        @Getter
        List<AfternoteResponse> content,

        @Schema(description = "현재 페이지 번호", example = "0")
        @Getter
        int page,

        @Schema(description = "요청한 사이즈", example = "10")
        @Getter
        int size,

        @Schema(description = "다음 페이지 존재 여부", example = "true")
        @Getter
        boolean hasNext
) {

    
    
    
}
