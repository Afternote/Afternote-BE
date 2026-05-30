package com.afternote.domain.appversion.dto;

import com.afternote.domain.appversion.model.AppPlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "앱 버전 릴리스 등록 요청")
@Getter
@NoArgsConstructor
public class AppVersionReleaseRegisterRequest {

    @NotNull
    @Schema(description = "플랫폼", example = "ANDROID", requiredMode = Schema.RequiredMode.REQUIRED)
    private AppPlatform platform;

    @NotNull
    @Min(1)
    @Schema(description = "versionCode", example = "10002", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer versionCode;

    @Schema(description = "versionName (기록용)", example = "1.0.1")
    private String versionName;

    @NotBlank
    @Schema(description = "Play Store URL", example = "https://play.google.com/store/apps/details?id=com.afternote", requiredMode = Schema.RequiredMode.REQUIRED)
    private String storeUrl;
}
