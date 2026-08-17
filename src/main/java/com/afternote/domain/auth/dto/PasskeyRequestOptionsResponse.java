package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
public record PasskeyRequestOptionsResponse(
        @Schema(description = "base64url challenge")
        String challenge,
        Long timeout,
        String rpId,
        @Schema(description = "usernameless 로그인이라 비움")
        List<Object> allowCredentials,
        String userVerification
) {
}
