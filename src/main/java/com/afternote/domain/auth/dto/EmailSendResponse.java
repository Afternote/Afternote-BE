package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
public record EmailSendResponse(
        @Schema(description = "인증번호 만료 시각 (UTC)", example = "2026-07-06T13:45:30Z")
        @Getter
        Instant expiresAt
) {
    public static EmailSendResponse of(Instant expiresAt) {
        return EmailSendResponse.builder()
                .expiresAt(expiresAt)
                .build();
    }
}
