package com.afternote.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeletePushTokenRequest(
        @Schema(description = "해제할 FCM registration token")
        @NotBlank
        @Size(max = 512)
        String token
) {
}
