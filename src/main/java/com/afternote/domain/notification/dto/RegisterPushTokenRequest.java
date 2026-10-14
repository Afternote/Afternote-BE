package com.afternote.domain.notification.dto;

import com.afternote.domain.notification.model.PushPlatform;
import com.afternote.domain.notification.model.NotificationPermission;
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
        PushPlatform platform,

        @Schema(description = "이 토큰의 앱 알림 권한. 생략하면 기존 상태 유지")
        NotificationPermission notificationPermission,

        @Schema(description = "앱 설치 UUID. 토큰이 바뀌어도 유지. 권한 보고 API와 동일 값")
        java.util.UUID installationId
) {
    public RegisterPushTokenRequest(String token, PushPlatform platform, NotificationPermission permission) {
        this(token, platform, permission, null);
    }
    public RegisterPushTokenRequest(String token, PushPlatform platform) {
        this(token, platform, null, null);
    }
}
