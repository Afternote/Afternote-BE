package com.afternote.domain.notification.dto;

import com.afternote.domain.notification.model.PushPlatform;
import com.afternote.domain.notification.model.NotificationPermission;
import com.afternote.domain.notification.model.UserPushToken;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PushTokenResponse(
        @Schema(description = "등록된 FCM token")
        String token,

        @Schema(description = "기기 플랫폼")
        PushPlatform platform,

        @Schema(description = "마지막 등록(갱신) 시각")
        LocalDateTime lastSeenAt,

        @Schema(description = "이 토큰의 직전 UNREGISTERED 판정 시각. 실제 발송 성공 전까지 유지")
        LocalDateTime invalidatedAt,

        @Schema(description = "직전 토큰 무효 사유. 현재는 UNREGISTERED만 사용")
        String invalidationReason,

        @Schema(description = "앱에서 토큰 재설정 안내 배너를 표시해야 하는지")
        boolean requiresReset,

        @Schema(description = "이 토큰의 앱 알림 권한. DENIED는 앱 권한 안내 배너 대상")
        NotificationPermission notificationPermission
) {

    public static PushTokenResponse from(UserPushToken pushToken) {
        return PushTokenResponse.builder()
                .token(pushToken.getToken())
                .platform(pushToken.getPlatform())
                .lastSeenAt(pushToken.getLastSeenAt())
                .invalidatedAt(pushToken.getInvalidatedAt())
                .invalidationReason(pushToken.getInvalidationReason())
                .notificationPermission(pushToken.effectivePermission())
                .requiresReset(pushToken.getInvalidatedAt() != null)
                .build();
    }
}
