package com.afternote.global.config;

import com.afternote.domain.receiver.controller.ReceiverInvitationController;
import com.afternote.domain.receiver.service.ReceiverInvitationService;
import com.afternote.global.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(controllers = ReceiverInvitationController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(SwaggerConfig.class)
@ImportAutoConfiguration({org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class})
class ReceiverInvitationOpenApiGeneratedDocsTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ReceiverInvitationService service;
    private JsonNode docs() throws Exception {
        return mapper.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode resolve(JsonNode docs, JsonNode schema) {
        return schema.has("$ref") ? docs.at(schema.get("$ref").asText().substring(1)) : schema;
    }
    @Test void generatedStatusesAndOwnedDetailMatchTheNewApi() throws Exception {
        JsonNode d = docs();
        assertThat(d.at("/components/schemas/OwnedInvitationResponse/properties/receiverId/nullable").asBoolean()).isTrue();
        var values = new java.util.ArrayList<String>();
        d.at("/components/schemas/OwnedInvitationResponse/properties/status/enum").forEach(n -> values.add(n.asText()));
        assertThat(values).containsExactlyInAnyOrder("PENDING", "ACCEPTED", "DECLINED", "EXPIRED");
        assertThat(d.path("paths").has("/api/v1/receiver-invitations/receivers/{receiverId}")).isTrue();
        JsonNode operation = d.path("paths").path("/api/v1/receiver-invitations/owned/{invitationId}").path("get");
        for (JsonNode param : operation.path("parameters")) assertThat(param.path("name").asText()).isNotEqualTo("userId");
        assertThat(operation.path("responses").has("404")).isTrue();
    }
    @Test void declineDocumentsClosedExpiredAndEmptyResponse() throws Exception {
        JsonNode d = docs();
        JsonNode operation = d.path("paths").path("/api/v1/receiver-invitations/{token}/decline").path("post");
        for (String code : java.util.List.of("200", "400", "401", "404", "409", "410")) {
            assertThat(operation.path("responses").has(code)).as(code).isTrue();
            JsonNode schema = resolve(d, operation.path("responses").path(code).at("/content/application~1json/schema"));
            assertThat(schema.at("/properties/data/nullable").asBoolean()).as(code).isTrue();
            assertThat(schema.at("/properties/data/$ref").isMissingNode()).isTrue();
        }
        assertThat(operation.at("/responses/409/description").asText()).contains("1910");
    }
    @Test void publicLookupDoesNotAdvertiseLoginAndCreateDocuments14Days() throws Exception {
        JsonNode d = docs();
        JsonNode lookup = d.path("paths").path("/api/v1/receiver-invitations/{token}").path("get");
        assertThat(lookup.path("security").isMissingNode() || lookup.path("security").isEmpty()).isTrue();
        assertThat(d.path("paths").path("/api/v1/receiver-invitations").path("post").path("description").asText()).contains("14일");
    }
}
