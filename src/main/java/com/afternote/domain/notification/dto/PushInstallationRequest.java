package com.afternote.domain.notification.dto;

import com.afternote.domain.notification.model.NotificationPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "FCM 토큰이 없어도 보고할 수 있는 사용자·설치별 OS 알림 권한")
public record PushInstallationRequest(
        @Schema(description = "설치마다 생성하고 토큰 회전에도 유지하는 UUID. 토큰 등록의 installationId와 동일 값",
                example = "d92560f1-ad73-4bfb-8759-017c61ad5168")
        @NotNull UUID installationId,
        @Schema(description = "앱에서 확인한 OS 알림 권한. DENIED이면 권한 안내 배너 대상")
        @NotNull NotificationPermission permission
) { }
