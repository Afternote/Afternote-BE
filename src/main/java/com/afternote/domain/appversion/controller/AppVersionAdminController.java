package com.afternote.domain.appversion.controller;

import com.afternote.domain.appversion.dto.AppVersionReleaseRegisterRequest;
import com.afternote.domain.appversion.dto.AppVersionReleaseResponse;
import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.service.AppVersionAdminService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin App Version API", description = "관리자 앱 버전 릴리스 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/app-versions")
public class AppVersionAdminController {

    private final AppVersionAdminService appVersionAdminService;

    @Operation(
            summary = "앱 버전 릴리스 등록",
            description = """
                    새 versionCode 릴리스를 DB에 기록합니다. 스플래시 버전 확인 API는 가장 높은 versionCode를 최신으로 사용합니다.

                    - 동일 platform + versionCode 중복 등록 불가
                    - 새 versionCode는 기존 최신보다 커야 합니다.
                    """
    )
    @PostMapping
    public ApiResponse<AppVersionReleaseResponse> registerRelease(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody AppVersionReleaseRegisterRequest request
    ) {
        return ApiResponse.success(appVersionAdminService.registerRelease(userId, request));
    }

    @Operation(summary = "앱 버전 릴리스 목록 조회", description = "플랫폼별 versionCode 내림차순으로 전체 릴리스 이력을 조회합니다.")
    @GetMapping
    public ApiResponse<List<AppVersionReleaseResponse>> listReleases(
            @Parameter(hidden = true) @UserId Long userId,
            @Parameter(description = "플랫폼", example = "ANDROID", required = true)
            @RequestParam AppPlatform platform
    ) {
        return ApiResponse.success(appVersionAdminService.listReleases(userId, platform));
    }
}
