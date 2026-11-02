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
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.dto.AfternotedetailResponse;
import com.afternote.domain.afternote.dto.AfternoteCreateRequest.CredentialsRequest;
import com.afternote.domain.afternote.dto.AfternoteCreateRequest.PlaylistRequest;
import com.afternote.domain.afternote.dto.AfternoteCreateRequest.SongRequest;
import java.time.LocalDateTime;
import org.mockito.ArgumentMatchers;
import org.mockito.BDDMockito;


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
    @DisplayName("생성 OpenAPI에서 임시저장 객체는 anyOf로 nullable을, 발행 계정 정보는 필수 참조를 표현한다")
    void generatedOpenApi_PreservesNullableObjectFields() throws Exception {
        JsonNode schemas = fetchSchemas();

        JsonNode draftCredentials = schemas.at("/AfternoteDraftAccountDetailResponse/properties/credentials");
        assertNullableObjectField(draftCredentials, "SOCIAL/BUSINESS");

        JsonNode draftPlaylist = schemas.at("/AfternoteDraftPlaylistDetailResponse/properties/playlist");
        assertNullableObjectField(draftPlaylist, "PLAYLIST");

        JsonNode publishedCredentials = schemas.at("/AfternotePublishedAccountDetailResponse/properties/credentials");
        assertThat(publishedCredentials.path("$ref").asText()).isEqualTo("#/components/schemas/AfternotePublishedCredentialsResponse");

        JsonNode actions = schemas.at("/AfternoteDraftAccountDetailResponse/properties/actions");
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
                "#/components/schemas/AfternoteDraftAccountDetailResponse",
                "#/components/schemas/AfternotePublishedAccountDetailResponse",
                "#/components/schemas/AfternotePublishedPlaylistDetailResponse"
        );

        JsonNode draftRequired = schemas.at("/AfternoteDraftAccountDetailResponse/required");
        assertThat(textValues(draftRequired)).contains(
                "afternoteId", "category", "title", "isDraft", "receivers", "updatedAt"
        );
        assertThat(textValues(draftRequired)).doesNotContain("playlist", "credentials");

        JsonNode publishedPlaylistRequired = schemas.at("/AfternotePublishedPlaylistDetailResponse/required");
        assertThat(textValues(publishedPlaylistRequired)).contains("playlist");

        JsonNode playlistField = schemas.at("/AfternotePublishedPlaylistDetailResponse/properties/playlist");
        assertThat(playlistField.path("nullable").asBoolean(false)).isFalse();
        assertThat(playlistField.path("$ref").asText()).isEqualTo("#/components/schemas/AfternotePublishedPlaylistResponse");

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
        assertThat(propertyNames(schemas.get("AfternoteDraftAccountDetailResponse"))).containsExactlyInAnyOrderElementsOf(detailFields);
        assertThat(propertyNames(schemas.get("AfternotePublishedAccountDetailResponse"))).containsExactlyInAnyOrderElementsOf(detailFields);
        assertThat(propertyNames(schemas.get("AfternotePublishedPlaylistDetailResponse"))).containsExactlyInAnyOrderElementsOf(detailFields);

        JsonNode leaveMessage = schemas.at("/AfternoteDraftAccountDetailResponse/properties/leaveMessage");
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
        assertThat(patchPlaylist.path("description").asText())
                .contains("JSON null")
                .contains("삭제")
                .contains("songs 생략");
        assertThat(textValues(schemas.at("/AfternoteUpdateRequest/required"))).doesNotContain("playlist");

        JsonNode playlistRequest = schemas.get("PlaylistRequest");
        assertThat(playlistRequest)
                .as("schemaNames=%s", schemaNames(schemas))
                .isNotNull();
        assertThat(textValues(playlistRequest.path("required")))
                .as("PATCH와 공유하는 PlaylistRequest.songs 는 생략 가능해야 한다. schema=%s", playlistRequest)
                .doesNotContain("songs");
        JsonNode requestSongs = playlistRequestSongs(playlistRequest);
        assertThat(requestSongs.path("description").asText())
                .as("PlaylistRequest=%s songs=%s", playlistRequest, requestSongs)
                .contains("생략")
                .contains("기존 곡 유지");
        assertThat(requestSongs.path("nullable").asBoolean(false)).isTrue();

        JsonNode publishedPlaylist = schemas.get("AfternotePublishedPlaylistResponse");
        assertThat(propertyNames(publishedPlaylist))
                .as("AfternotePublishedPlaylistResponse=%s", publishedPlaylist)
                .contains("memorialAudioUrl", "memorialPhotoUrl", "memorialVideo", "songs");
        assertThat(textValues(publishedPlaylist.path("required")))
                .doesNotContain("memorialPhotoUrlSpecified", "memorialVideoSpecified", "memorialAudioUrlSpecified");
    }

    @Test
    @DisplayName("생성 OpenAPI에서 PlaylistRequest 미디어·곡 필드가 properties로 나온다")
    void generatedOpenApi_PlaylistRequestDocumentsMediaFields() throws Exception {
        JsonNode schemas = fetchSchemas();
        JsonNode playlist = schemas.get("PlaylistRequest");
        assertThat(playlist).isNotNull();
        assertThat(propertyNames(playlist))
                .as("PlaylistRequest=%s", playlist)
                .contains("atmosphere", "memorialPhotoUrl", "memorialAudioUrl", "memorialVideo", "songs")
                .doesNotContain(
                        "memorialPhotoUrlSpecified",
                        "memorialVideoSpecified",
                        "memorialAudioUrlSpecified"
                );

        assertThat(playlist.at("/properties/memorialPhotoUrl/description").asText())
                .contains("JSON null")
                .contains("삭제");
        assertThat(playlist.at("/properties/memorialAudioUrl/description").asText())
                .contains("JSON null")
                .contains("삭제");
        assertThat(playlist.at("/properties/memorialAudioUrl/nullable").asBoolean()).isTrue();
        assertThat(playlist.at("/properties/songs").isMissingNode()).isFalse();

        JsonNode updatePlaylist = schemas.at("/AfternoteUpdateRequest/properties/playlist");
        assertThat(updatePlaylist.isMissingNode()).isFalse();
        assertThat(updatePlaylist.path("description").asText()).contains("JSON null").contains("삭제");
    }

    @Test
    @DisplayName("8개 종류/상태의 실제 HTTP JSON과 스키마 분기가 일치하고 nullable·필수값을 구분한다")
    void categoryStateContractsMatchActualHttpJson() throws Exception {
        JsonNode docs = fetchDocs();
        JsonNode schemas = docs.at("/components/schemas");
        var examples = objectMapper.createArrayNode();
        var categories = AfternoteCategoryType.values();
        for (var category : categories) {
            for (boolean draft : java.util.List.of(true, false)) {
                boolean account = category.name().equals("SOCIAL") || category.name().equals("BUSINESS");
                boolean playlist = category.name().equals("PLAYLIST");
                var credentials = account && !draft
                        ? new CredentialsRequest("dummy-id", "dummy-password") : null;
                var music = playlist && !draft
                        ? new PlaylistRequest(
                                "차분하게", null, java.util.List.of(new SongRequest("곡", "가수", null)), null, null) : null;
                var dto = AfternotedetailResponse.of(
                        1L, category, "테스트", draft, playlist ? null : java.util.List.of("정리"),
                        java.util.List.of(), credentials, java.util.List.of(), music, LocalDateTime.of(2026, 10, 29, 12, 0));
                BDDMockito.given(afternoteService.getDetailAfternote(ArgumentMatchers.any(), ArgumentMatchers.eq(1L))).willReturn(dto);
                var result = mockMvc.perform(get("/api/v1/afternotes/1").requestAttr("userId", 1L))
                        .andExpect(status().isOk()).andReturn();
                JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
                String name = "Afternote" + (draft ? "Draft" : "Published")
                        + (account ? "Account" : playlist ? "Playlist" : "Gallery") + "DetailResponse";
                JsonNode schema = schemas.path(name);
                assertThat(propertyNames(schema)).containsExactlyInAnyOrderElementsOf(propertyNamesOfObject(payload));
                assertThat(schema.at("/properties/isDraft/enum/0").asBoolean()).isEqualTo(draft);
                assertThat(textValues(schema.at("/properties/category/enum"))).contains(category.name());
                long matches = refs(schemas.at("/AfternotedetailResponse/oneOf")).stream()
                        .map(ref -> schemas.path(ref.substring(ref.lastIndexOf('/') + 1)))
                        .filter(candidate -> textValues(candidate.at("/properties/category/enum")).contains(category.name())
                                && candidate.at("/properties/isDraft/enum/0").asBoolean() == draft).count();
                assertThat(matches).isEqualTo(1);
                assertThat(textValues(schema.path("required"))).contains("category", "isDraft");
                if (account && !draft) {
                    assertThat(textValues(schema.path("required"))).contains("credentials");
                    assertThat(payload.at("/credentials/id").asText()).isEqualTo("dummy-id");
                    assertThat(payload.at("/credentials/password").asText()).isEqualTo("dummy-password");
                } else assertThat(payload.path("credentials").isNull()).isTrue();
                if (!playlist || draft) assertThat(payload.path("playlist").isNull()).isTrue();
                if (!account) assertThat(schema.at("/properties/credentials/enum/0").isNull()).isTrue();
                if (!playlist) assertThat(schema.at("/properties/playlist/enum/0").isNull()).isTrue();
                if (playlist && !draft) assertThat(textValues(schema.path("required"))).contains("playlist");
                examples.addObject().put("schema", name).set("payload", payload);
            }
        }
        for (var category : java.util.List.of(AfternoteCategoryType.SOCIAL, AfternoteCategoryType.BUSINESS, AfternoteCategoryType.PLAYLIST)) {
            boolean music = category == AfternoteCategoryType.PLAYLIST;
            var dto = AfternotedetailResponse.of(1L, category, "부분 작성", true,
                    null, null, music ? null : new CredentialsRequest("dummy-id", null), java.util.List.of(),
                    music ? new PlaylistRequest(null, null, java.util.List.of(), null, null) : null,
                    LocalDateTime.of(2026, 10, 29, 12, 0));
            BDDMockito.given(afternoteService.getDetailAfternote(ArgumentMatchers.any(), ArgumentMatchers.eq(1L))).willReturn(dto);
            var result = mockMvc.perform(get("/api/v1/afternotes/1").requestAttr("userId", 1L))
                    .andExpect(status().isOk()).andReturn();
            JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsByteArray()).path("data");
            if (music) assertThat(payload.at("/playlist/songs").size()).isZero();
            else assertThat(payload.at("/credentials/password").isNull()).isTrue();
            examples.addObject().put("schema", "partial-draft-" + category).set("payload", payload);
        }
        JsonNode requiredCredentials = schemas.path("AfternotePublishedCredentialsResponse");
        assertThat(textValues(requiredCredentials.path("required"))).containsExactlyInAnyOrder("id", "password");
        for (String field : java.util.List.of("id", "password")) {
            assertThat(requiredCredentials.at("/properties/" + field + "/nullable").asBoolean()).isFalse();
            assertThat(requiredCredentials.at("/properties/" + field + "/minLength").asInt()).isEqualTo(1);
            assertThat(schemas.at("/AfternoteDraftCredentialsResponse/properties/" + field + "/nullable").asBoolean()).isTrue();
        }
        // Export the generated document and synthetic HTTP responses for independent OpenAPI validation.
        var directory = java.nio.file.Path.of("build/reports/afternote-contract");
        java.nio.file.Files.createDirectories(directory);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("openapi.json").toFile(), docs);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("responses.json").toFile(), examples);
    }

    private static java.util.List<String> propertyNamesOfObject(JsonNode value) {
        var names = new java.util.ArrayList<String>();
        value.fieldNames().forEachRemaining(names::add);
        return names;
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
        assertThat(field.has("anyOf")).isTrue();
        assertThat(field.get("anyOf").isArray()).isTrue();
        assertThat(field.get("anyOf").size()).isGreaterThanOrEqualTo(1);
        assertThat(field.get("anyOf").get(0).has("$ref")).isTrue();
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

    private static JsonNode playlistRequestSongs(JsonNode playlistRequest) {
        JsonNode direct = playlistRequest.at("/properties/songs");
        if (!direct.isMissingNode()) {
            return direct;
        }
        JsonNode allOf = playlistRequest.get("allOf");
        if (allOf != null && allOf.isArray()) {
            for (JsonNode part : allOf) {
                JsonNode songs = part.at("/properties/songs");
                if (!songs.isMissingNode()) {
                    return songs;
                }
            }
        }
        return direct;
    }
}
