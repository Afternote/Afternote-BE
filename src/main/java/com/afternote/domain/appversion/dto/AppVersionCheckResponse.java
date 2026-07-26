package com.afternote.domain.appversion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Schema(description = "앱 버전 확인 응답")
public record AppVersionCheckResponse(
        @Schema(description = "업데이트 필요 여부 (true면 스플래시 진입 불가)", example = "false")
        @Getter
        boolean updateRequired,

        @Schema(description = "스토어 최신 versionCode", example = "10001")
        @Getter
        int latestVersionCode,

        @Schema(description = "Play Store URL (updateRequired=true일 때만 포함)", example = "https://play.google.com/store/apps/details?id=com.afternote")
        @Getter
        String storeUrl
) {



}
