package com.afternote.domain.notification.service;

import com.afternote.domain.notification.dto.*;
import com.afternote.domain.notification.model.*;
import com.afternote.domain.user.controller.UserController;
import com.afternote.domain.user.service.UserService;
import com.afternote.global.exception.GlobalExceptionHandler;
import com.afternote.global.resolver.UserIdArgumentResolver;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PushTokenApiContractTest {
    private final UserPushTokenService service=mock(UserPushTokenService.class);
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new UserController(mock(UserService.class),service))
            .setControllerAdvice(new GlobalExceptionHandler()).setCustomArgumentResolvers(new UserIdArgumentResolver()).build();
    private static final String INSTALL="d92560f1-ad73-4bfb-8759-017c61ad5168";

    @Test void legacyTokenRequestReturnsNewInvalidationFieldsWithoutExtraRequiredInputs() throws Exception {
        when(service.registerOrRefresh(eq(7L),any())).thenReturn(new PushTokenResponse("token",PushPlatform.ANDROID,
                LocalDateTime.of(2026,10,21,12,0),LocalDateTime.of(2026,10,20,12,0),"UNREGISTERED",true,NotificationPermission.UNKNOWN));
        mvc.perform(put("/api/v1/users/push-tokens").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE,7L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"token\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requiresReset").value(true))
                .andExpect(jsonPath("$.data.invalidationReason").value("UNREGISTERED"))
                .andExpect(jsonPath("$.data.notificationPermission").value("UNKNOWN"));
        verify(service).registerOrRefresh(7L,new RegisterPushTokenRequest("token",PushPlatform.ANDROID));
    }
    @Test void deniedPermissionReportWorksWithoutToken() throws Exception {
        when(service.reportPermission(eq(7L),any())).thenReturn(new PushInstallationResponse(INSTALL,NotificationPermission.DENIED,true));
        mvc.perform(put("/api/v1/users/push-installations").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE,7L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"installationId\":\""+INSTALL+"\",\"permission\":\"DENIED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.showPermissionBanner").value(true));
        verify(service).reportPermission(7L,new PushInstallationRequest(UUID.fromString(INSTALL),NotificationPermission.DENIED));
    }
    @ParameterizedTest
    @ValueSource(strings={"{}", "{\"permission\":\"DENIED\"}",
            "{\"installationId\":\"bad\",\"permission\":\"DENIED\"}",
            "{\"installationId\":\"d92560f1-ad73-4bfb-8759-017c61ad5168\",\"permission\":\"INVALID\"}"})
    void invalidPermissionReportsReturnDocumented400(String request) throws Exception {
        mvc.perform(put("/api/v1/users/push-installations").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE,7L)
                .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1400));
        verifyNoInteractions(service);
    }
    @Test void notificationApiRequiresAuthenticatedIdentity() throws Exception {
        mvc.perform(put("/api/v1/users/push-installations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"installationId\":\""+INSTALL+"\",\"permission\":\"DENIED\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
