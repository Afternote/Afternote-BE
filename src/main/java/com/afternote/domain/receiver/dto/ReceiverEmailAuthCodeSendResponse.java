package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Schema(description = "수신자 이메일 인증번호 발송 응답")
public record ReceiverEmailAuthCodeSendResponse(
        @Schema(
                description = "인증번호 만료 시각 (UTC, RFC 3339)",
                example = "2026-07-06T13:45:30Z",
                type = "string",
                format = "date-time",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        Instant expiresAt,

        @Schema(
                description = "인증번호 유효 시간(초). 수신자 이메일은 300(5분). 회원 가입 메일의 180과 다르다.",
                example = "300",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        int ttlSeconds
) {
    public static ReceiverEmailAuthCodeSendResponse of(Instant expiresAt, int ttlSeconds) {
        return ReceiverEmailAuthCodeSendResponse.builder()
                .expiresAt(expiresAt)
                .ttlSeconds(ttlSeconds)
                .build();
    }
}
