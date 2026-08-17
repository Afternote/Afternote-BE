package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PasskeyResponse(
        Long id,
        @Schema(example = "패스키")
        String displayName,
        LocalDateTime createdAt
) {
}
