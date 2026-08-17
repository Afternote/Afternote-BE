package com.afternote.domain.notification.dto;

import com.afternote.domain.notification.model.PushPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterPushTokenRequest(
        @Schema(description = "Firebase FCM registration token", example = "dXyz...")
        @NotBlank
        @Size(max = 512)
        String token,

        @Schema(description = "기기 플랫폼", example = "ANDROID")
        @NotNull
        PushPlatform platform
) {
}
