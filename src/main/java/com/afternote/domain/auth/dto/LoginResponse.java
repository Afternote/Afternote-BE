package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
public record LoginResponse(
        @Schema(description = "리프레쉬 토큰" ,example = "safasfskldfjasdkgwr34tklgfdldfsdckl...")
        @Getter
        String refreshToken,

        @Schema(description = "액세스 토큰" ,example = "safasfskldfjasdkgwr34tklgfdldfsdckl...")
        @Getter
        String accessToken,

        @Schema(description = "액세스 토큰 유효 시간(초)", example = "3600")
        @Getter
        long expiresIn
) {




}
