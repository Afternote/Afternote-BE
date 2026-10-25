package com.afternote.global.config;

import com.afternote.domain.admin.controller.AdminController;
import com.afternote.domain.admin.service.AdminService;
import com.afternote.domain.notification.policy.NotificationDispatchRepository;
import com.afternote.domain.notification.policy.NotificationInboxController;
import com.afternote.domain.notification.service.AdminPushTestService;
import com.afternote.domain.notification.service.UserPushTokenService;
import com.afternote.domain.user.controller.UserController;
import com.afternote.domain.user.service.UserService;
import com.afternote.global.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Assert the actual /v3/api-docs output, not only annotation presence. */
@WebMvcTest(controllers = {UserController.class, NotificationInboxController.class, AdminController.class},
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(SwaggerConfig.class)
@ImportAutoConfiguration({org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class})
class NotificationOpenApiGeneratedDocsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserService users;
    @MockBean UserPushTokenService tokens;
    @MockBean AdminService admins;
    @MockBean AdminPushTestService pushTests;
    @MockBean NotificationDispatchRepository notifications;

    private JsonNode docs() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }
    private static JsonNode op(JsonNode docs, String path, String method) { return docs.path("paths").path(path).path(method); }
    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>(); node.forEach(n -> values.add(n.asText())); return values;
    }
    private static JsonNode resolve(JsonNode docs, JsonNode node) {
        if (node.has("$ref")) return docs.at(node.path("$ref").asText().substring(1));
        if (node.has("allOf")) for (JsonNode child : node.path("allOf")) if (child.has("$ref")) return resolve(docs, child);
        return node;
    }
    private static JsonNode responseData(JsonNode docs, JsonNode operation) {
        JsonNode wrapper = resolve(docs, operation.at("/responses/200/content/application~1json/schema"));
        return resolve(docs, wrapper.at("/properties/data"));
    }
    private static void responses(JsonNode operation, String... codes) {
        for (String code : codes) assertThat(operation.path("responses").has(code)).as("response %s", code).isTrue();
    }

    @Test void allNotificationOperationsHideInjectedUserIdAndDocumentAuthentication() throws Exception {
        JsonNode docs = docs();
        String[][] operations = {{"/api/v1/users/push-tokens", "put"}, {"/api/v1/users/push-tokens", "delete"},
                {"/api/v1/users/push-installations", "put"}, {"/api/v1/users/notifications", "get"},
                {"/api/v1/users/notifications/{id}/read", "patch"}, {"/api/v1/admin/push-tokens/test", "post"}};
        for (String[] endpoint : operations) {
            JsonNode operation = op(docs, endpoint[0], endpoint[1]);
            assertThat(operation.isMissingNode()).isFalse();
            assertThat(operation.path("summary").asText()).isNotBlank();
            for (JsonNode parameter : operation.path("parameters")) assertThat(parameter.path("name").asText()).isNotEqualTo("userId");
            responses(operation, "200", "400", "401");
        }
        assertThat(docs.at("/security/0/bearer-key").isArray()).isTrue();
    }
    @Test void tokenSchemaPreservesOldRequestAndDocumentsInvalidationSignal() throws Exception {
        JsonNode docs=docs();
        JsonNode request=docs.at("/components/schemas/RegisterPushTokenRequest");
        assertThat(strings(request.path("required"))).containsExactlyInAnyOrder("token", "platform");
        assertThat(request.at("/properties/token/maxLength").asInt()).isEqualTo(512);
        assertThat(request.at("/properties/installationId/format").asText()).isEqualTo("uuid");
        assertThat(strings(resolve(docs, request.at("/properties/notificationPermission")).path("enum")))
                .containsExactlyInAnyOrder("UNKNOWN", "GRANTED", "DENIED");
        JsonNode response=responseData(docs,op(docs,"/api/v1/users/push-tokens","put"));
        assertThat(response.at("/properties/invalidatedAt/nullable").asBoolean()).isTrue();
        assertThat(response.at("/properties/invalidationReason/nullable").asBoolean()).isTrue();
        assertThat(response.at("/properties/requiresReset/type").asText()).isEqualTo("boolean");
        assertThat(op(docs,"/api/v1/users/push-tokens","put").path("description").asText()).contains("requiresReset", "재등록");
    }
    @Test void installationPermissionNeedsUuidAndPermissionButNoToken() throws Exception {
        JsonNode docs=docs();
        JsonNode request=docs.at("/components/schemas/PushInstallationRequest");
        assertThat(strings(request.path("required"))).containsExactlyInAnyOrder("installationId","permission");
        assertThat(request.at("/properties/token").isMissingNode()).isTrue();
        assertThat(request.at("/properties/installationId/format").asText()).isEqualTo("uuid");
        JsonNode response=responseData(docs,op(docs,"/api/v1/users/push-installations","put"));
        assertThat(response.at("/properties/showPermissionBanner/description").asText()).contains("DENIED");
        assertThat(response.at("/properties/showPermissionBanner/type").asText()).isEqualTo("boolean");
    }
    @Test void inboxDocumentsPaginationPayloadNullableFieldsAndReadErrors() throws Exception {
        JsonNode docs=docs();
        JsonNode list=op(docs,"/api/v1/users/notifications","get");
        assertThat(list.path("description").asText()).contains("50", "CANCELLED");
        JsonNode page=null;
        for (JsonNode parameter:list.path("parameters")) if (parameter.path("name").asText().equals("page")) page=parameter;
        assertThat(page).isNotNull();
        assertThat(page.at("/schema/minimum").asInt()).isZero();
        assertThat(page.at("/schema/type").asText()).isEqualTo("integer");
        assertThat(page.at("/schema/default").asInt()).isZero();
        JsonNode inbox=responseData(docs,list);
        assertThat(inbox.at("/properties/unreadCount/type").asText()).isEqualTo("integer");
        JsonNode item=resolve(docs,inbox.at("/properties/items/items"));
        assertThat(item.at("/properties/data/additionalProperties/type").asText()).isEqualTo("string");
        assertThat(item.at("/properties/readAt/nullable").asBoolean()).isTrue();
        assertThat(item.at("/properties/deliveryReason/nullable").asBoolean()).isTrue();
        assertThat(item.at("/properties/status/description").asText()).contains("ALIMTALK_PENDING", "미발송");
        responses(op(docs,"/api/v1/users/notifications/{id}/read","patch"),"200","400","401","404");
    }
    @Test void adminTestDocumentsOptionalStringMapAndDoesNotPromiseDelivery() throws Exception {
        JsonNode docs=docs();
        JsonNode request=docs.at("/components/schemas/AdminPushTestRequest");
        assertThat(strings(request.path("required"))).doesNotContain("data");
        assertThat(request.at("/properties/data/additionalProperties/type").asText()).isEqualTo("string");
        JsonNode operation=op(docs,"/api/v1/admin/push-tokens/test","post");
        responses(operation,"200","400","401","403","404","503");
        assertThat(operation.path("description").asText()).contains("기기 도착을 보장하지", "진단용");
        assertThat(responseData(docs,operation).at("/properties/tokenCount/description").asText()).contains("등록된 토큰", "실제 발송");
    }
    @Test void errorsAndEmptySuccessResponsesUseNullableDataInsteadOfSuccessDtos() throws Exception {
        JsonNode docs=docs();
        String[][] endpoints={{"/api/v1/users/push-tokens","put"},{"/api/v1/users/push-tokens","delete"},
                {"/api/v1/users/push-installations","put"},{"/api/v1/users/notifications","get"},
                {"/api/v1/users/notifications/{id}/read","patch"},{"/api/v1/admin/push-tokens/test","post"}};
        for (String[] endpoint:endpoints) {
            JsonNode operation=op(docs,endpoint[0],endpoint[1]);
            var responseFields=operation.path("responses").fields();
            while(responseFields.hasNext()) {
                var response=responseFields.next();
                if (!response.getKey().matches("[45]\\d\\d")) continue;
                JsonNode schema=resolve(docs,response.getValue().at("/content/application~1json/schema"));
                assertThat(strings(schema.path("required"))).containsExactlyInAnyOrder("status","code","message","data");
                assertThat(schema.at("/properties/data/nullable").asBoolean()).isTrue();
                assertThat(schema.at("/properties/data/$ref").isMissingNode()).isTrue();
            }
        }
        assertThat(responseData(docs,op(docs,"/api/v1/users/push-tokens","delete")).path("nullable").asBoolean()).isTrue();
        assertThat(responseData(docs,op(docs,"/api/v1/users/notifications/{id}/read","patch")).path("nullable").asBoolean()).isTrue();
    }
}
