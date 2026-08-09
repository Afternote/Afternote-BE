package com.afternote.domain.appversion.controller;

import com.afternote.domain.appversion.dto.AppVersionCheckResponse;
import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.service.AppVersionService;
import com.afternote.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "App Version API", description = "앱 버전 확인 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/app/version")
public class AppVersionController {

    private final AppVersionService appVersionService;

    @Operation(
            summary = "앱 버전 확인 API",
            description = """
                    스플래시 시작 시 호출합니다. JWT 인증 없이 사용합니다.

                    - `versionCode`만 비교합니다.
                    - `versionCode` < 서버 `latestVersionCode` 이면 `updateRequired=true`와 `storeUrl`을 반환합니다.
                    - API 호출 실패 또는 `updateRequired=true`이면 클라이언트에서 스플래시 진입을 막습니다.
                    """
    )
    @SecurityRequirements // 전역 bearer 상속 제거 → OpenAPI security: []
    @GetMapping
    public ApiResponse<AppVersionCheckResponse> checkVersion(
            @Parameter(description = "플랫폼", example = "ANDROID", required = true)
            @RequestParam AppPlatform platform,
            @Parameter(description = "앱 현재 versionCode", example = "10001", required = true)
            @RequestParam @Min(1) int versionCode
    ) {
        return ApiResponse.success(appVersionService.checkVersion(platform, versionCode));
    }
}
