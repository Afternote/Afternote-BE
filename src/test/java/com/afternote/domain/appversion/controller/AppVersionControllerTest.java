package com.afternote.domain.appversion.controller;

import com.afternote.domain.appversion.dto.AppVersionCheckResponse;
import com.afternote.domain.appversion.model.AppPlatform;
import com.afternote.domain.appversion.service.AppVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppVersionControllerTest {

    @InjectMocks
    private AppVersionController appVersionController;

    @Mock
    private AppVersionService appVersionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(appVersionController).build();
    }

    @Test
    @DisplayName("앱 버전 확인 API 성공 - 업데이트 불필요")
    void checkVersion_upToDate() throws Exception {
        given(appVersionService.checkVersion(AppPlatform.ANDROID, 10001))
                .willReturn(new AppVersionCheckResponse(false, 10001, null));

        mockMvc.perform(get("/api/v1/app/version")
                        .param("platform", "ANDROID")
                        .param("versionCode", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.updateRequired").value(false))
                .andExpect(jsonPath("$.data.latestVersionCode").value(10001))
                .andExpect(jsonPath("$.data.storeUrl").doesNotExist());

        verify(appVersionService).checkVersion(AppPlatform.ANDROID, 10001);
    }

    @Test
    @DisplayName("앱 버전 확인 API 성공 - 업데이트 필요")
    void checkVersion_updateRequired() throws Exception {
        given(appVersionService.checkVersion(AppPlatform.ANDROID, 10001))
                .willReturn(new AppVersionCheckResponse(
                        true,
                        10002,
                        "https://play.google.com/store/apps/details?id=com.afternote"
                ));

        mockMvc.perform(get("/api/v1/app/version")
                        .param("platform", "ANDROID")
                        .param("versionCode", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updateRequired").value(true))
                .andExpect(jsonPath("$.data.latestVersionCode").value(10002))
                .andExpect(jsonPath("$.data.storeUrl")
                        .value("https://play.google.com/store/apps/details?id=com.afternote"));
    }
}
