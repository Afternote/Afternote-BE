package com.afternote.domain.afternote.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
public record AfternoteCreateResponse(
        @Schema(description = "생성된 애프터노트 ID", example = "3")
        @Getter
        Long afternoteId
) {

}
