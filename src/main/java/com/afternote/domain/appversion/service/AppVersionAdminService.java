package com.afternote.domain.appversion.service;

import com.afternote.domain.admin.service.AdminService;
import com.afternote.domain.appversion.dto.AppVersionReleaseRegisterRequest;
import com.afternote.domain.appversion.dto.AppVersionReleaseResponse;
import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.model.AppVersionRelease;
import com.afternote.domain.appversion.repository.AppVersionReleaseRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppVersionAdminService {

    private final AdminService adminService;
    private final AppVersionReleaseRepository appVersionReleaseRepository;

    @Transactional
    public AppVersionReleaseResponse registerRelease(Long userId, AppVersionReleaseRegisterRequest request) {
        adminService.validateAdmin(userId);

        if (request.getPlatform() != AppPlatform.ANDROID) {
            throw new CustomException(ErrorCode.UNSUPPORTED_APP_PLATFORM);
        }

        int versionCode = request.getVersionCode();
        if (appVersionReleaseRepository.existsByPlatformAndVersionCode(request.getPlatform(), versionCode)) {
            throw new CustomException(ErrorCode.APP_VERSION_RELEASE_DUPLICATE);
        }

        appVersionReleaseRepository.findFirstByPlatformOrderByVersionCodeDesc(request.getPlatform())
                .ifPresent(latest -> {
                    if (versionCode <= latest.getVersionCode()) {
                        throw new CustomException(ErrorCode.APP_VERSION_RELEASE_VERSION_CODE_NOT_GREATER);
                    }
                });

        AppVersionRelease saved = appVersionReleaseRepository.save(AppVersionRelease.create(
                request.getPlatform(),
                versionCode,
                request.getVersionName(),
                request.getStoreUrl().trim()
        ));

        return AppVersionReleaseResponse.from(saved);
    }

    public List<AppVersionReleaseResponse> listReleases(Long userId, AppPlatform platform) {
        adminService.validateAdmin(userId);
        if (platform != AppPlatform.ANDROID) {
            throw new CustomException(ErrorCode.UNSUPPORTED_APP_PLATFORM);
        }
        return appVersionReleaseRepository.findAllByPlatformOrderByVersionCodeDesc(platform).stream()
                .map(AppVersionReleaseResponse::from)
                .toList();
    }
}
