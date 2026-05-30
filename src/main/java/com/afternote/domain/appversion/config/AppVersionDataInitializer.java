package com.afternote.domain.appversion.config;

import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.model.AppVersionRelease;
import com.afternote.domain.appversion.repository.AppVersionReleaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppVersionDataInitializer {

    private final AppVersionReleaseRepository appVersionReleaseRepository;

    @Value("${app.version.seed.android.version-code:10001}")
    private int seedAndroidVersionCode;

    @Value("${app.version.seed.android.version-name:1.0.0}")
    private String seedAndroidVersionName;

    @Value("${app.version.seed.android.store-url:}")
    private String seedAndroidStoreUrl;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedInitialReleaseIfEmpty() {
        if (appVersionReleaseRepository.count() > 0) {
            return;
        }
        if (!StringUtils.hasText(seedAndroidStoreUrl)) {
            log.warn("앱 버전 릴리스가 비어 있고 seed store-url도 없습니다. 관리자 API로 등록해 주세요.");
            return;
        }

        appVersionReleaseRepository.save(AppVersionRelease.create(
                AppPlatform.ANDROID,
                seedAndroidVersionCode,
                seedAndroidVersionName,
                seedAndroidStoreUrl
        ));
        log.info("초기 Android 앱 버전 릴리스 등록: versionCode={}, versionName={}",
                seedAndroidVersionCode, seedAndroidVersionName);
    }
}
