package com.afternote.domain.appversion.dto;

import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.model.AppVersionRelease;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "앱 버전 릴리스 기록")
public record AppVersionReleaseResponse(
        @Schema(description = "릴리스 ID", example = "1")
        @Getter
        Long id,

        @Schema(description = "플랫폼", example = "ANDROID")
        @Getter
        AppPlatform platform,

        @Schema(description = "versionCode", example = "10001")
        @Getter
        int versionCode,

        @Schema(description = "versionName", example = "1.0.0")
        @Getter
        String versionName,

        @Schema(description = "스토어 URL")
        @Getter
        String storeUrl,

        @Schema(description = "등록 일시")
        @Getter
        LocalDateTime createdAt
) {







    public static AppVersionReleaseResponse from(AppVersionRelease release) {
        return new AppVersionReleaseResponse(
                release.getId(),
                release.getPlatform(),
                release.getVersionCode(),
                release.getVersionName(),
                release.getStoreUrl(),
                release.getCreatedAt()
        );
    }
}
