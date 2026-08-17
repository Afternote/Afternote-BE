package com.afternote.domain.notification.dto;

import com.afternote.domain.notification.model.PushPlatform;
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
        LocalDateTime lastSeenAt
) {

    public static PushTokenResponse from(UserPushToken pushToken) {
        return PushTokenResponse.builder()
                .token(pushToken.getToken())
                .platform(pushToken.getPlatform())
                .lastSeenAt(pushToken.getLastSeenAt())
                .build();
    }
}
