package com.afternote.domain.appversion.dto;

import com.afternote.domain.appversion.model.AppPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Schema(description = "앱 버전 릴리스 등록 요청")
public record AppVersionReleaseRegisterRequest(
        @NotNull
        @Schema(description = "플랫폼", example = "ANDROID", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        AppPlatform platform,

        @NotNull
        @Min(1)
        @Schema(description = "versionCode", example = "10002", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Integer versionCode,

        @Schema(description = "versionName (기록용)", example = "1.0.1")
        @Getter
        String versionName,

        @NotBlank
        @Schema(description = "Play Store URL", example = "https://play.google.com/store/apps/details?id=com.afternote", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String storeUrl
) {




}
