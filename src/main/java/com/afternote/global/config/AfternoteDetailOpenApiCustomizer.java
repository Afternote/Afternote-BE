package com.afternote.global.config;

import com.afternote.domain.afternote.dto.AfternoteCreateRequest;
import com.afternote.domain.afternote.dto.AfternotedetailResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAPI 3.0에서 $ref 는 형제 속성(nullable, description)을 가질 수 없다.
 * springdoc가 객체 필드를 $ref 로 접으면 애노테이션의 nullable·설명이 유실되므로
 * allOf: [ { $ref }, { nullable, description } ] 형태로 감싼다.
 */
public class AfternoteDetailOpenApiCustomizer implements OpenApiCustomizer {

    static final List<String> UNION_SCHEMA_NAMES = List.of(
            "AfternoteDraftDetailResponse",
            "AfternotePublishedDetailResponse",
            "AfternotePublishedPlaylistDetailResponse"
    );

    static final List<String> ALWAYS_REQUIRED = List.of(
            "afternoteId", "category", "title", "isDraft", "receivers", "updatedAt"
    );

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();

        ensurePlaylistRequestProperties(schemas);

        wrapNullableRef(
                schemas.get("AfternoteDraftDetailResponse"),
                "credentials",
                AfternotedetailResponse.CREDENTIALS_DESCRIPTION
        );
        wrapNullableRef(
                schemas.get("AfternoteDraftDetailResponse"),
                "playlist",
                AfternotedetailResponse.DRAFT_PLAYLIST_DESCRIPTION
        );
        wrapNullableRef(
                schemas.get("AfternotePublishedDetailResponse"),
                "credentials",
                AfternotedetailResponse.CREDENTIALS_DESCRIPTION
        );
        wrapNullableRef(
                schemas.get("AfternotePublishedDetailResponse"),
                "playlist",
                AfternotedetailResponse.PUBLISHED_PLAYLIST_OPTIONAL_DESCRIPTION
        );
        wrapNullableRef(
                schemas.get("AfternotePublishedPlaylistDetailResponse"),
                "credentials",
                AfternotedetailResponse.CREDENTIALS_DESCRIPTION
        );
        ensureRequiredRef(
                schemas.get("AfternotePublishedPlaylistDetailResponse"),
                "playlist",
                "플레이리스트 정보. 발행 완료 PLAYLIST는 필수이며 최소 1곡"
        );
        flattenSelfAllOf(schemas.get("AfternotePublishedPlaylistResponse"));
        ensureSongsRequired(schemas.get("AfternotePublishedPlaylistResponse"));
        wrapNullableRef(schemas.get("AfternotePublishedPlaylistResponse"), "memorialVideo", "추모 영상");
        wrapNullableRef(schemas.get("AfternotePublishedPlaylistResponse"), "memorialAudioUrl", "추모 음성 URL");
        wrapNullableRef(
                schemas.get("AfternoteUpdateRequest"),
                "playlist",
                "플레이리스트 (PLAYLIST). playlist 객체 생략 시 플레이리스트 전체 유지. "
                        + "정식 등록 상태면 요청·기존 합쳐 필수. "
                        + "memorialPhotoUrl·memorialVideo·memorialAudioUrl 은 필드 생략 시 유지, "
                        + "JSON null 이면 해당 미디어를 삭제한다(DB 참조 제거 + S3 객체 삭제). "
                        + "값이 있으면 교체(업로드로 발급된 afternotes 키만 허용)."
        );

        replaceUnionSchema(schemas);
        ensureAlwaysRequired(schemas);
    }

    /**
     * springdoc는 커스텀 JsonDeserializer가 붙은 PlaylistRequest의 필드를 비운다.
     * PATCH omit/null 구분은 런타임 deserializer가 맡고, 문서의 필드 목록만 여기서 채운다.
     */
    @SuppressWarnings("rawtypes")
    static void ensurePlaylistRequestProperties(Map<String, Schema> schemas) {
        Schema playlist = schemas.computeIfAbsent("PlaylistRequest", name -> {
            Schema schema = new Schema<>();
            schema.setType("object");
            return schema;
        });
        if (playlist.getType() == null) {
            playlist.setType("object");
        }

        Map<String, Schema> properties = playlist.getProperties() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(playlist.getProperties());
        properties.remove("memorialPhotoUrlSpecified");
        properties.remove("memorialVideoSpecified");
        properties.remove("memorialAudioUrlSpecified");

        String prefix = AfternoteCreateRequest.PlaylistRequest.PATCH_MEDIA_DESCRIPTION_PREFIX;
        putStringProperty(properties, "atmosphere", "분위기 설명", false);
        putStringProperty(
                properties,
                "memorialPhotoUrl",
                prefix + "영정 사진 URL. 생성 시 생략/null 이면 없음.",
                true
        );
        if (!hasUsableProperty(properties, "songs")) {
            ArraySchema songs = new ArraySchema();
            songs.setDescription("노래 목록");
            songs.setItems(new Schema<>().$ref(componentRef(schemas, "SongRequest")));
            properties.put("songs", songs);
        }
        if (!hasUsableProperty(properties, "memorialVideo")) {
            Schema ref = new Schema<>().$ref(componentRef(schemas, "MemorialVideoRequest"));
            properties.put(
                    "memorialVideo",
                    toAllOf(
                            ref,
                            prefix + "추모 영상. 생성 시 생략/null 이면 없음. "
                                    + "PATCH에서 null 이면 영상·썸네일을 함께 삭제한다.",
                            true
                    )
            );
        } else {
            Schema video = properties.get("memorialVideo");
            if (video.getDescription() == null || video.getDescription().isBlank()) {
                video.setDescription(
                        prefix + "추모 영상. 생성 시 생략/null 이면 없음. "
                                + "PATCH에서 null 이면 영상·썸네일을 함께 삭제한다."
                );
            }
            if (video.getNullable() == null) {
                video.setNullable(true);
            }
        }
        putStringProperty(
                properties,
                "memorialAudioUrl",
                prefix + "추모 음성 URL. 생성 시 생략/null 이면 없음. "
                        + "플레이리스트당 1개(mp3/m4a/wav).",
                true
        );

        playlist.setProperties(properties);
        if (playlist.getDescription() == null || playlist.getDescription().isBlank()) {
            playlist.setDescription("플레이리스트 미디어·곡. PATCH에서 필드 생략 시 유지, JSON null 이면 삭제.");
        }
    }

    @SuppressWarnings("rawtypes")
    private static boolean hasUsableProperty(Map<String, Schema> properties, String name) {
        Schema property = properties.get(name);
        if (property == null) {
            return false;
        }
        return property.get$ref() != null
                || property.getType() != null
                || property.getItems() != null
                || (property.getAllOf() != null && !property.getAllOf().isEmpty())
                || (property.getProperties() != null && !property.getProperties().isEmpty());
    }

    @SuppressWarnings("rawtypes")
    private static void putStringProperty(
            Map<String, Schema> properties,
            String name,
            String description,
            boolean nullable
    ) {
        Schema existing = properties.get(name);
        if (hasUsableProperty(properties, name)) {
            if (existing.getDescription() == null || existing.getDescription().isBlank()) {
                existing.setDescription(description);
            }
            if (nullable && existing.getNullable() == null) {
                existing.setNullable(true);
            }
            return;
        }
        StringSchema schema = new StringSchema();
        schema.setDescription(description);
        if (nullable) {
            schema.setNullable(true);
        }
        properties.put(name, schema);
    }

    @SuppressWarnings("rawtypes")
    private static String componentRef(Map<String, Schema> schemas, String preferredName) {
        if (schemas.containsKey(preferredName)) {
            return "#/components/schemas/" + preferredName;
        }
        for (String name : schemas.keySet()) {
            if (name.endsWith(preferredName)) {
                return "#/components/schemas/" + name;
            }
        }
        return "#/components/schemas/" + preferredName;
    }

    private void replaceUnionSchema(Map<String, Schema> schemas) {
        Schema<?> union = schemas.computeIfAbsent("AfternotedetailResponse", name -> new Schema<>());
        union.setType(null);
        union.setProperties(null);
        union.setRequired(null);
        union.set$ref(null);
        union.setDescription("애프터노트 상세 응답. isDraft와 category에 따라 필수 필드가 다르다.");
        List<Schema> oneOf = new ArrayList<>();
        for (String name : UNION_SCHEMA_NAMES) {
            oneOf.add(new Schema<>().$ref("#/components/schemas/" + name));
        }
        union.setOneOf(oneOf);
    }

    private void ensureAlwaysRequired(Map<String, Schema> schemas) {
        for (String name : List.of(
                "AfternoteDraftDetailResponse",
                "AfternotePublishedDetailResponse",
                "AfternotePublishedPlaylistDetailResponse"
        )) {
            Schema<?> schema = schemas.get(name);
            if (schema == null) {
                continue;
            }
            List<String> required = schema.getRequired() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(schema.getRequired());
            for (String field : ALWAYS_REQUIRED) {
                if (!required.contains(field)) {
                    required.add(field);
                }
            }
            if ("AfternotePublishedPlaylistDetailResponse".equals(name) && !required.contains("playlist")) {
                required.add("playlist");
            }
            schema.setRequired(required);
        }
    }

    @SuppressWarnings("rawtypes")
    static void flattenSelfAllOf(Schema<?> schema) {
        if (schema == null || schema.getAllOf() == null) {
            return;
        }
        Map<String, Schema> merged = schema.getProperties() == null
                ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(schema.getProperties());
        for (Object item : schema.getAllOf()) {
            if (!(item instanceof Schema<?> part)) {
                continue;
            }
            if (part.getProperties() != null) {
                merged.putAll(part.getProperties());
            }
        }
        if (!merged.isEmpty()) {
            schema.setProperties(merged);
        }
        schema.setAllOf(null);
        schema.set$ref(null);
        if (schema.getType() == null) {
            schema.setType("object");
        }
    }

    @SuppressWarnings("rawtypes")
    static void ensureSongsRequired(Schema<?> playlistSchema) {
        if (playlistSchema == null) {
            return;
        }
        List<String> required = playlistSchema.getRequired() == null
                ? new ArrayList<>()
                : new ArrayList<>(playlistSchema.getRequired());
        if (!required.contains("songs")) {
            required.add("songs");
        }
        playlistSchema.setRequired(required);
        if (playlistSchema.getProperties() != null) {
            Schema songs = (Schema) playlistSchema.getProperties().get("songs");
            if (songs != null) {
                io.swagger.v3.oas.models.media.ArraySchema array = songs instanceof io.swagger.v3.oas.models.media.ArraySchema arraySchema
                        ? arraySchema
                        : new io.swagger.v3.oas.models.media.ArraySchema();
                if (!(songs instanceof io.swagger.v3.oas.models.media.ArraySchema)) {
                    array.setItems(songs.getItems());
                    array.setDescription(songs.getDescription());
                    playlistSchema.getProperties().put("songs", array);
                }
                array.setMinItems(1);
                array.setNullable(false);
                if (array.getDescription() == null || array.getDescription().isBlank()) {
                    array.setDescription("노래 목록. 발행 완료 PLAYLIST는 최소 1곡");
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    static void wrapNullableRef(Schema<?> parent, String propertyName, String description) {
        if (parent == null || parent.getProperties() == null) {
            return;
        }
        Schema property = (Schema) parent.getProperties().get(propertyName);
        if (property == null) {
            return;
        }
        parent.getProperties().put(propertyName, toAllOf(property, description, true));
        if (parent.getRequired() != null) {
            parent.getRequired().remove(propertyName);
        }
    }

    @SuppressWarnings("rawtypes")
    static void ensureRequiredRef(Schema<?> parent, String propertyName, String description) {
        if (parent == null || parent.getProperties() == null) {
            return;
        }
        Schema property = (Schema) parent.getProperties().get(propertyName);
        if (property == null) {
            return;
        }
        parent.getProperties().put(propertyName, toAllOf(property, description, false));
    }

    @SuppressWarnings("rawtypes")
    private static Schema toAllOf(Schema property, String description, boolean nullable) {
        String ref = firstRef(property);
        String resolvedDescription = firstNonBlank(property.getDescription(), description);
        if (ref == null) {
            if (nullable) {
                property.setNullable(true);
            }
            if (property.getDescription() == null || property.getDescription().isBlank()) {
                property.setDescription(resolvedDescription);
            }
            return property;
        }
        if (property.get$ref() == null
                && property.getAllOf() != null
                && Boolean.valueOf(nullable).equals(property.getNullable())
                && resolvedDescription != null
                && resolvedDescription.equals(property.getDescription())) {
            return property;
        }
        ComposedSchema wrapped = new ComposedSchema();
        wrapped.setNullable(nullable);
        wrapped.setDescription(resolvedDescription);
        wrapped.addAllOfItem(new Schema<>().$ref(ref));
        return wrapped;
    }

    @SuppressWarnings("rawtypes")
    private static String firstRef(Schema property) {
        if (property.get$ref() != null) {
            return property.get$ref();
        }
        if (property.getAllOf() != null) {
            for (Object item : property.getAllOf()) {
                if (item instanceof Schema<?> schema && schema.get$ref() != null) {
                    return schema.get$ref();
                }
            }
        }
        return null;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }
}
