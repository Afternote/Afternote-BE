package com.afternote.global.config;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.*;
import java.util.*;

/** Category/state views of the existing JSON. Request schemas are deliberately not mutated. */
final class AfternoteDetailVariants {
    private AfternoteDetailVariants() { }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void apply(Map<String, Schema> schemas) {
        Schema draft = schemas.get("AfternoteDraftDetailResponse");
        Schema published = schemas.get("AfternotePublishedDetailResponse");
        Schema playlist = schemas.get("AfternotePublishedPlaylistDetailResponse");
        if (draft == null || published == null || playlist == null) return;

        Schema video = copy(schemas.get("MemorialVideoRequest"));
        AfternoteDetailOpenApiCustomizer.flattenSelfAllOf(video);
        video.setNullable(false);
        video.setDescription("추모 영상 응답. 업로드 키 대신 조회 가능한 URL을 반환한다.");
        schemas.put("AfternoteDetailVideoResponse", video);
        schemas.put("AfternoteDraftCredentialsResponse", credentials(false));
        schemas.put("AfternotePublishedCredentialsResponse", credentials(true));
        Schema draftPlaylist = copy(schemas.get("PlaylistRequest"));
        draftPlaylist.setAllOf(null);
        draftPlaylist.setNullable(false);
        draftPlaylist.setDescription("임시저장 PLAYLIST의 데이터. 미작성 값과 빈 곡 목록을 허용한다.");
        Map<String, Schema> properties = draftPlaylist.getProperties();
        properties.get("atmosphere").setNullable(true);
        properties.get("memorialPhotoUrl").setDescription("영정 사진 URL. 없으면 null");
        properties.get("memorialAudioUrl").setDescription("추모 음성 URL. 없으면 null");
        properties.put("memorialVideo", nullableRef("AfternoteDetailVideoResponse", "추모 영상. 없으면 null"));
        Schema songs = properties.get("songs");
        songs.setDescription("임시저장 곡 목록. 빈 목록을 허용한다.");
        songs.setMinItems(null);
        schemas.put("AfternoteDraftPlaylistResponse", draftPlaylist);
        Schema publishedPlaylist = schemas.get("AfternotePublishedPlaylistResponse");
        ((Schema) publishedPlaylist.getProperties().get("atmosphere")).setNullable(true);
        publishedPlaylist.getProperties().put("memorialVideo", nullableRef("AfternoteDetailVideoResponse", "추모 영상. 없으면 null"));

        List<String> drafts = new ArrayList<>();
        List<String> publications = new ArrayList<>();
        for (boolean isDraft : List.of(true, false)) {
            for (String group : List.of("Account", "Gallery", "Playlist")) {
                String name = "Afternote" + (isDraft ? "Draft" : "Published") + group + "DetailResponse";
                Schema variant = copy(isDraft ? draft : group.equals("Playlist") ? playlist : published);
                variant.setDescription((isDraft ? "임시저장 " : "발행 완료 ") + group
                        + " 상세. category와 isDraft로 구분하며 기존 JSON 키와 null 필드를 유지한다.");
                Map<String, Schema> fields = variant.getProperties();
                fields.put("updatedAt", new StringSchema().format("local-date-time")
                        .description("최종 수정일. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)")
                        .example("2026-10-29T12:00:00"));
                fields.put("category", new StringSchema()._enum(group.equals("Account")
                        ? List.of("SOCIAL", "BUSINESS") : List.of(group.toUpperCase(Locale.ROOT))));
                fields.put("isDraft", new BooleanSchema()._enum(List.of(isDraft)));
                boolean account = group.equals("Account");
                boolean music = group.equals("Playlist");
                fields.put("credentials", account
                        ? isDraft ? nullableRef("AfternoteDraftCredentialsResponse", "SOCIAL/BUSINESS 계정 정보. 미작성 시 null")
                        : ref("AfternotePublishedCredentialsResponse")
                        : nullOnly("이 종류에는 계정 정보가 없으므로 항상 null"));
                fields.put("playlist", music
                        ? isDraft ? nullableRef("AfternoteDraftPlaylistResponse", "PLAYLIST 데이터. 미작성 시 null")
                        : ref("AfternotePublishedPlaylistResponse")
                        : nullOnly("이 종류에는 플레이리스트가 없으므로 항상 null"));
                if (music) fields.put("actions", nullOnly("PLAYLIST에는 체크리스트가 없으므로 항상 null"));
                List<String> required = new ArrayList<>(AfternoteDetailOpenApiCustomizer.ALWAYS_REQUIRED);
                if (!isDraft && account) required.add("credentials");
                if (!isDraft && music) required.add("playlist");
                variant.setRequired(required);
                schemas.put(name, variant);
                (isDraft ? drafts : publications).add(name);
            }
        }
        // Keep old component names as aliases for clients that reference them directly.
        schemas.put("AfternoteDraftDetailResponse", union(drafts));
        schemas.put("AfternotePublishedDetailResponse", union(publications.subList(0, 2)));
        List<String> all = new ArrayList<>(drafts);
        all.addAll(publications);
        schemas.put("AfternotedetailResponse", union(all));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema credentials(boolean published) {
        Schema schema = new ObjectSchema();
        schema.setDescription(published ? "발행 계정 정보. id와 password는 필수이며 공백을 허용하지 않는다."
                : "임시저장 계정 정보. id와 password의 부분 작성을 허용한다.");
        for (String key : List.of("id", "password")) {
            StringSchema value = new StringSchema();
            value.setNullable(!published);
            if (published) { value.setMinLength(1); }
            schema.addProperty(key, value);
        }
        if (published) schema.setRequired(List.of("id", "password"));
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private static Schema copy(Schema schema) {
        return Json.mapper().convertValue(schema, Schema.class);
    }

    @SuppressWarnings("rawtypes")
    private static Schema ref(String name) { return new Schema<>().$ref("#/components/schemas/" + name); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullOnly(String description) {
        Schema schema = new Schema<>().type("object").nullable(true).description(description);
        schema.setEnum(Collections.singletonList(null));
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private static Schema nullableRef(String name, String description) {
        // In OpenAPI 3.0 nullable on an allOf wrapper cannot relax a non-null $ref.
        return new ComposedSchema().description(description).nullable(true)
                .anyOf(List.of(ref(name), nullOnly("미작성")));
    }

    @SuppressWarnings("rawtypes")
    private static Schema union(List<String> names) {
        ComposedSchema schema = new ComposedSchema();
        schema.setDescription("category와 isDraft로 구분하는 상세 응답. JSON 키와 중첩 구조는 기존과 동일하다.");
        for (String name : names) schema.addOneOfItem(ref(name));
        return schema;
    }
}
