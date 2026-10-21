package com.afternote.domain.notification.dto;

import com.afternote.domain.notification.model.NotificationPermission;
import io.swagger.v3.oas.annotations.media.Schema;

public record PushInstallationResponse(
        @Schema(description = "보고한 앱 설치 UUID", format = "uuid", requiredMode = Schema.RequiredMode.REQUIRED)
        String installationId,
        @Schema(description = "이 사용자·설치에 저장된 OS 알림 권한", requiredMode = Schema.RequiredMode.REQUIRED)
        NotificationPermission permission,
        @Schema(description = "권한 거부 안내 배너 표시 여부. permission=DENIED일 때 true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean showPermissionBanner
) { }
