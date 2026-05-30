package com.afternote.domain.appversion.service;

import com.afternote.domain.appversion.dto.AppVersionCheckResponse;
import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.model.AppVersionRelease;
import com.afternote.domain.appversion.repository.AppVersionReleaseRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AppVersionServiceTest {

    @InjectMocks
    private AppVersionService appVersionService;

    @Mock
    private AppVersionReleaseRepository appVersionReleaseRepository;

    @Test
    @DisplayName("DB 최신 versionCode 이상이면 업데이트 불필요")
    void checkVersion_upToDate() {
        given(appVersionReleaseRepository.findFirstByPlatformOrderByVersionCodeDesc(AppPlatform.ANDROID))
                .willReturn(Optional.of(latestRelease(10002, "https://play.google.com/app")));

        AppVersionCheckResponse response = appVersionService.checkVersion(AppPlatform.ANDROID, 10002);

        assertThat(response.isUpdateRequired()).isFalse();
        assertThat(response.getLatestVersionCode()).isEqualTo(10002);
        assertThat(response.getStoreUrl()).isNull();
    }

    @Test
    @DisplayName("DB 최신 versionCode보다 낮으면 업데이트 필요")
    void checkVersion_updateRequired() {
        given(appVersionReleaseRepository.findFirstByPlatformOrderByVersionCodeDesc(AppPlatform.ANDROID))
                .willReturn(Optional.of(latestRelease(10002, "https://play.google.com/app")));

        AppVersionCheckResponse response = appVersionService.checkVersion(AppPlatform.ANDROID, 10001);

        assertThat(response.isUpdateRequired()).isTrue();
        assertThat(response.getLatestVersionCode()).isEqualTo(10002);
        assertThat(response.getStoreUrl()).isEqualTo("https://play.google.com/app");
    }

    @Test
    @DisplayName("릴리스 기록이 없으면 미설정 오류")
    void checkVersion_notConfigured() {
        given(appVersionReleaseRepository.findFirstByPlatformOrderByVersionCodeDesc(AppPlatform.ANDROID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> appVersionService.checkVersion(AppPlatform.ANDROID, 10001))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.APP_VERSION_NOT_CONFIGURED);
    }

    private static AppVersionRelease latestRelease(int versionCode, String storeUrl) {
        return AppVersionRelease.create(AppPlatform.ANDROID, versionCode, "1.0.0", storeUrl);
    }
}
