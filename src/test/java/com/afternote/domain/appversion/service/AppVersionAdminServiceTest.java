package com.afternote.domain.appversion.service;
import com.afternote.domain.admin.service.AdminService;
import com.afternote.domain.appversion.dto.AppVersionReleaseRegisterRequest;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AppVersionAdminServiceTest {

    @InjectMocks
    private AppVersionAdminService appVersionAdminService;

    @Mock
    private AdminService adminService;

    @Mock
    private AppVersionReleaseRepository appVersionReleaseRepository;

    @Test
    @DisplayName("이미 존재하는 versionCode 등록 시 충돌")
    void registerRelease_duplicate() {
        doNothing().when(adminService).validateAdmin(1L);
        given(appVersionReleaseRepository.existsByPlatformAndVersionCode(AppPlatform.ANDROID, 10002))
                .willReturn(true);

        AppVersionReleaseRegisterRequest request = request(10002);

        assertThatThrownBy(() -> appVersionAdminService.registerRelease(1L, request))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.APP_VERSION_RELEASE_DUPLICATE);

        verify(appVersionReleaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("최신보다 낮거나 같은 versionCode 등록 시 거절")
    void registerRelease_notGreaterThanLatest() {
        doNothing().when(adminService).validateAdmin(1L);
        given(appVersionReleaseRepository.existsByPlatformAndVersionCode(AppPlatform.ANDROID, 10001))
                .willReturn(false);
        given(appVersionReleaseRepository.findFirstByPlatformOrderByVersionCodeDesc(AppPlatform.ANDROID))
                .willReturn(Optional.of(
                        AppVersionRelease.create(AppPlatform.ANDROID, 10002, "1.0.0", "https://play.google.com/app")
                ));

        assertThatThrownBy(() -> appVersionAdminService.registerRelease(1L, request(10001)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.APP_VERSION_RELEASE_VERSION_CODE_NOT_GREATER);
    }

    private static AppVersionReleaseRegisterRequest request(int versionCode) {
        return new AppVersionReleaseRegisterRequest(
                AppPlatform.ANDROID,
                versionCode,
                null,
                "https://play.google.com/app"
        );
    }
}
