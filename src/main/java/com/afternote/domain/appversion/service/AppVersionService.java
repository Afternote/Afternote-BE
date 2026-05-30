package com.afternote.domain.appversion.service;

import com.afternote.domain.appversion.dto.AppVersionCheckResponse;
import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.model.AppVersionRelease;
import com.afternote.domain.appversion.repository.AppVersionReleaseRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppVersionService {

    private final AppVersionReleaseRepository appVersionReleaseRepository;

    public AppVersionCheckResponse checkVersion(AppPlatform platform, int versionCode) {
        if (platform != AppPlatform.ANDROID) {
            throw new CustomException(ErrorCode.UNSUPPORTED_APP_PLATFORM);
        }

        AppVersionRelease latest = appVersionReleaseRepository
                .findFirstByPlatformOrderByVersionCodeDesc(platform)
                .orElseThrow(() -> new CustomException(ErrorCode.APP_VERSION_NOT_CONFIGURED));

        int latestVersionCode = latest.getVersionCode();
        boolean updateRequired = versionCode < latestVersionCode;

        if (!updateRequired) {
            return new AppVersionCheckResponse(false, latestVersionCode, null);
        }

        String storeUrl = latest.getStoreUrl();
        if (!StringUtils.hasText(storeUrl)) {
            throw new CustomException(ErrorCode.APP_VERSION_NOT_CONFIGURED);
        }

        return new AppVersionCheckResponse(true, latestVersionCode, storeUrl);
    }
}
