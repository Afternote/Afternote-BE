package com.afternote.global.config;

import com.afternote.domain.notification.engagement.*;
import com.afternote.domain.notification.policy.*;
import com.afternote.domain.notification.reminder.*;
import com.afternote.global.jwt.JwtAuthenticationFilter;
import com.afternote.global.resolver.UserIdArgumentResolver;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.*;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={NotificationPreferencesController.class,NotificationLinkController.class,NotificationConfirmationWebController.class,NotificationNavigationController.class,TimeLetterDeliveryController.class},
        excludeAutoConfiguration={SecurityAutoConfiguration.class,SecurityFilterAutoConfiguration.class},
        excludeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters=false)
@Import(SwaggerConfig.class)
@ImportAutoConfiguration({org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class})
class NonBillingNotificationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean EngagementNotificationService service;
    @MockBean TimeLetterDeliveryService delivery;
    @MockBean NotificationDispatchRepository dispatches;
    @MockBean EntityManager entityManager;
    @MockBean Clock clock;
    @Test void newPrivateRoutesRequireLogin() throws Exception {
        mvc.perform(get("/api/v1/users/me/notification-preferences")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/me/notification-preferences/entry")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/notifications/3/target")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/notification-links/token/cancel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,dispatches);
    }
    @Test void settingsRejectMissingAndOutOfRangeFields() throws Exception {
        mvc.perform(put("/api/v1/users/me/notification-preferences").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE,1L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"time\":\"18:00\",\"weekdays\":[],\"weeklyGoal\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(1400));
        verifyNoInteractions(service);
    }
    @Test void delegateWebPageEscapesTokenAndOnlyPostConfirms() throws Exception {
        String token="safe-token-for-test";
        when(service.link(token)).thenReturn(new EngagementNotificationService.LinkInfo("AGENT_CONFIRMATION",false));
        mvc.perform(get("/notification-confirmations/"+token)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("확인 접수하기")));
        verify(service,never()).confirm(anyString());
        when(service.confirm(token)).thenReturn(new EngagementNotificationService.LinkInfo("AGENT_CONFIRMATION",true));
        mvc.perform(post("/notification-confirmations/"+token+"/confirm")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("확인이 접수됐어요")));
    }
    @Test void settingsUseTypedWeekdaysAndTime() throws Exception {
        when(service.settings(eq(1L),eq(true),eq(LocalTime.of(18,0)),anySet(),eq(3))).thenReturn(new NotificationProfile(1L));
        mvc.perform(put("/api/v1/users/me/notification-preferences").requestAttr(UserIdArgumentResolver.USER_ID_ATTRIBUTE,1L)
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"time\":\"18:00\",\"weekdays\":[\"FRIDAY\"],\"weeklyGoal\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.weeklyGoal").value(3));
    }
    @Test void generatedDocsCoverAllNonBillingEndpointsAndNullErrorEnvelopes() throws Exception {
        JsonNode docs = mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        String[][] paths={{"/api/v1/users/me/notification-preferences","put"},{"/api/v1/users/me/notification-preferences/delegate","put"},
                {"/api/v1/users/me/notification-preferences/entry","post"},{"/api/v1/users/me/notification-preferences/anniversaries","post"},
                {"/api/v1/users/notifications/{id}/target","get"},{"/api/v1/time-letters/{id}/retry-delivery","post"},
                {"/api/v1/notification-links/{token}/confirm","post"},{"/api/v1/notification-links/{token}/cancel","post"}};
        for (String[] path:paths) {
            var op=docs.path("paths").path(path[0]).path(path[1]); assertThat(op.isMissingNode()).as(path[0]).isFalse();
            assertThat(op.path("responses").has("404")).as(path[0]).isTrue();
            for (var param:op.path("parameters")) assertThat(param.path("name").asText()).isNotIn("userId");
            var error=op.path("responses").path("404").at("/content/application~1json/schema");
            assertThat(error.path("$ref").asText()).endsWith("NotificationErrorResponse");
        }
        assertThat(docs.at("/components/schemas/SettingsRequest/properties/weekdays/items/enum").toString()).contains("FRIDAY");
        assertThat(docs.at("/components/schemas/Retry/properties/idempotencyKey/format").asText()).isEqualTo("uuid");
        assertThat(docs.at("/components/schemas/Target/properties/toast/nullable").asBoolean()).isTrue();
    }
}
