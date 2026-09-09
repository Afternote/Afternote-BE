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
                description = "인증번호 유효 시간(초). 회원가입·아이디/비밀번호 찾기는 180.",
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
