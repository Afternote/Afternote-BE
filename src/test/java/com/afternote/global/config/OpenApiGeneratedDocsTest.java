package com.afternote.global.config;

import com.afternote.domain.afternote.controller.AfternoteController;
import com.afternote.domain.afternote.service.AfternoteService;
import com.afternote.global.jwt.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #255·#256: 애노테이션 리플렉션이 아니라 실제 생성 OpenAPI(/v3/api-docs)를 고정한다.
 */
@WebMvcTest(
        controllers = AfternoteController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import({SwaggerConfig.class})
@ImportAutoConfiguration({
        org.springdoc.core.configuration.SpringDocConfiguration.class,
        org.springdoc.core.properties.SpringDocConfigProperties.class,
        org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class
})
class OpenApiGeneratedDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AfternoteService afternoteService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("생성 OpenAPI에서 credentials·playlist는 $ref 형제 속성이 아니라 allOf로 nullable·설명이 남는다")
    void generatedOpenApi_PreservesNullableObjectFields() throws Exception {
        JsonNode schemas = fetchSchemas();

        JsonNode draftCredentials = schemas.at("/AfternoteDraftDetailResponse/properties/credentials");
        assertNullableObjectField(draftCredentials, "SOCIAL/BUSINESS");

        JsonNode draftPlaylist = schemas.at("/AfternoteDraftDetailResponse/properties/playlist");
        assertNullableObjectField(draftPlaylist, "PLAYLIST");

        JsonNode publishedCredentials = schemas.at("/AfternotePublishedDetailResponse/properties/credentials");
        assertNullableObjectField(publishedCredentials, "SOCIAL/BUSINESS");

        JsonNode actions = schemas.at("/AfternoteDraftDetailResponse/properties/actions");
        assertThat(actions.path("nullable").asBoolean()).isTrue();
        assertThat(actions.path("description").asText()).contains("SOCIAL/BUSINESS/GALLERY");
    }

    @Test
    @DisplayName("생성 OpenAPI에서 draft/발행/발행 PLAYLIST 스키마가 구분되고 published PLAYLIST의 playlist는 required다")
    void generatedOpenApi_SplitsDraftAndPublishedPlaylistContracts() throws Exception {
        JsonNode docs = fetchDocs();
        JsonNode schemas = docs.at("/components/schemas");

        JsonNode union = schemas.get("AfternotedetailResponse");
        assertThat(union).isNotNull();
        assertThat(union.has("oneOf")).isTrue();
        assertThat(refs(union.get("oneOf"))).contains(
                "#/components/schemas/AfternoteDraftDetailResponse",
                "#/components/schemas/AfternotePublishedDetailResponse",
                "#/components/schemas/AfternotePublishedPlaylistDetailResponse"
        );

        JsonNode draftRequired = schemas.at("/AfternoteDraftDetailResponse/required");
        assertThat(textValues(draftRequired)).contains(
                "afternoteId", "category", "title", "isDraft", "receivers", "updatedAt"
        );
        assertThat(textValues(draftRequired)).doesNotContain("playlist", "credentials");

        JsonNode publishedPlaylistRequired = schemas.at("/AfternotePublishedPlaylistDetailResponse/required");
        assertThat(textValues(publishedPlaylistRequired)).contains("playlist");

        JsonNode playlistField = schemas.at("/AfternotePublishedPlaylistDetailResponse/properties/playlist");
        assertThat(playlistField.path("nullable").asBoolean(false)).isFalse();
        assertThat(playlistField.path("description").asText()).contains("필수");
        assertThat(playlistField.has("$ref")).isFalse();
        assertThat(playlistField.has("allOf")).isTrue();

        JsonNode songsRequired = schemas.at("/AfternotePublishedPlaylistResponse/required");
        assertThat(textValues(songsRequired))
                .as("AfternotePublishedPlaylistResponse.required should include songs; schema keys=%s",
                        schemaNames(schemas))
                .contains("songs");

        JsonNode getResponses = docs.at("/paths/~1api~1v1~1afternotes~1{afternoteId}/get/responses");
        assertThat(getResponses.path("200").isMissingNode()).isFalse();
        assertThat(getResponses.path("400").isMissingNode()).isFalse();
        assertThat(getResponses.path("401").isMissingNode()).isFalse();
        assertThat(getResponses.path("404").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("생성 OpenAPI는 GET 런타임 계약(필드명·required·400 코드)과 맞고 PATCH category 생략은 유지한다")
    void generatedOpenApi_MatchesRuntimeGetContractAndUnchangedPatch() throws Exception {
        JsonNode docs = fetchDocs();
        JsonNode schemas = docs.at("/components/schemas");

        java.util.List<String> detailFields = java.util.List.of(
                "afternoteId", "category", "title", "isDraft",
                "actions", "leaveMessage", "credentials", "receivers", "playlist", "updatedAt"
        );
        assertThat(propertyNames(schemas.get("AfternoteDraftDetailResponse"))).containsExactlyInAnyOrderElementsOf(detailFields);
        assertThat(propertyNames(schemas.get("AfternotePublishedDetailResponse"))).containsExactlyInAnyOrderElementsOf(detailFields);
        assertThat(propertyNames(schemas.get("AfternotePublishedPlaylistDetailResponse"))).containsExactlyInAnyOrderElementsOf(detailFields);

        JsonNode leaveMessage = schemas.at("/AfternoteDraftDetailResponse/properties/leaveMessage");
        assertThat(leaveMessage.path("nullable").asBoolean()).isTrue();
        assertThat(leaveMessage.path("description").asText()).contains("남기실 말씀");

        JsonNode publishedPlaylistPayload = schemas.get("AfternotePublishedPlaylistResponse");
        JsonNode publishedSongs = publishedPlaylistPayload.path("properties").path("songs");
        assertThat(publishedSongs.isMissingNode())
                .as("AfternotePublishedPlaylistResponse=%s", publishedPlaylistPayload)
                .isFalse();
        Integer minItems = publishedSongs.has("minItems") ? publishedSongs.path("minItems").asInt() : null;
        assertThat(minItems)
                .as("published songs schema=%s", publishedSongs)
                .isEqualTo(1);
        assertThat(publishedSongs.path("nullable").asBoolean(false)).isFalse();

        JsonNode getOp = docs.at("/paths/~1api~1v1~1afternotes~1{afternoteId}/get");
        assertThat(getOp.path("description").asText()).contains("PLAYLIST").contains("필수");
        assertThat(getOp.at("/responses/400/description").asText())
                .contains("1603")
                .contains("1609")
                .contains("1610");

        JsonNode patchBody = docs.at(
                "/paths/~1api~1v1~1afternotes~1{afternoteId}/patch/requestBody/content/application~1json/schema"
        );
        String patchRef = patchBody.path("$ref").asText();
        assertThat(patchRef).contains("AfternoteUpdateRequest");
        JsonNode patchRequired = schemas.at("/AfternoteUpdateRequest/required");
        assertThat(textValues(patchRequired)).doesNotContain("category");

        JsonNode patchOp = docs.at("/paths/~1api~1v1~1afternotes~1{afternoteId}/patch");
        assertThat(patchOp.path("description").asText()).contains("JSON null").contains("삭제");

        JsonNode patchPlaylist = schemas.at("/AfternoteUpdateRequest/properties/playlist");
        assertThat(patchPlaylist.path("description").asText()).contains("JSON null").contains("삭제");

        JsonNode publishedPlaylist = schemas.get("AfternotePublishedPlaylistResponse");
        assertThat(propertyNames(publishedPlaylist))
                .as("AfternotePublishedPlaylistResponse=%s", publishedPlaylist)
                .contains("memorialAudioUrl", "memorialPhotoUrl", "memorialVideo", "songs");
        assertThat(textValues(publishedPlaylist.path("required")))
                .doesNotContain("memorialPhotoUrlSpecified", "memorialVideoSpecified", "memorialAudioUrlSpecified");
    }

    private JsonNode fetchDocs() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode fetchSchemas() throws Exception {
        return fetchDocs().at("/components/schemas");
    }

    private static void assertNullableObjectField(JsonNode field, String descriptionFragment) {
        assertThat(field.isMissingNode()).isFalse();
        assertThat(field.has("$ref"))
                .as("OpenAPI 3.0 $ref cannot keep sibling nullable/description")
                .isFalse();
        assertThat(field.path("nullable").asBoolean()).isTrue();
        assertThat(field.path("description").asText()).contains(descriptionFragment);
        assertThat(field.has("allOf")).isTrue();
        assertThat(field.get("allOf").isArray()).isTrue();
        assertThat(field.get("allOf").size()).isGreaterThanOrEqualTo(1);
        assertThat(field.get("allOf").get(0).has("$ref")).isTrue();
    }

    private static java.util.List<String> refs(JsonNode oneOf) {
        java.util.List<String> refs = new java.util.ArrayList<>();
        for (JsonNode item : oneOf) {
            refs.add(item.path("$ref").asText());
        }
        return refs;
    }

    private static java.util.List<String> schemaNames(JsonNode schemas) {
        java.util.List<String> names = new java.util.ArrayList<>();
        schemas.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static java.util.List<String> propertyNames(JsonNode schema) {
        java.util.List<String> names = new java.util.ArrayList<>();
        schema.path("properties").fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static java.util.List<String> textValues(JsonNode array) {
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : array) {
            values.add(item.asText());
        }
        return values;
    }
}
