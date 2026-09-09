package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
public record EmailSendResponse(
        @Schema(description = "인증번호 만료 시각 (UTC)", example = "2026-07-06T13:45:30Z")
        @Getter
        Instant expiresAt,

        @Schema(
                description = "인증번호 유효 시간(초). 회원가입·찾기 메일은 180, 수신자 메일은 별도 계약.",
                example = "180",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        int ttlSeconds
) {
    public static EmailSendResponse of(Instant expiresAt, int ttlSeconds) {
        return EmailSendResponse.builder()
                .expiresAt(expiresAt)
                .ttlSeconds(ttlSeconds)
                .build();
    }
}
