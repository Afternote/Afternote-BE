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
        Instant expiresAt
) {
    public static ReceiverEmailAuthCodeSendResponse of(Instant expiresAt) {
        return ReceiverEmailAuthCodeSendResponse.builder()
                .expiresAt(expiresAt)
                .build();
    }
}
