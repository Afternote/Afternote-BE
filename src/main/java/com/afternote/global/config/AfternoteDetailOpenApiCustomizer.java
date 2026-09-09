package com.afternote.global.config;

import com.afternote.domain.afternote.dto.AfternotedetailResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.ArrayList;
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
                        + "songs 생략(null)은 기존 곡 유지, 빈 배열 [] 은 전부 삭제(발행 노트는 1610). "
                        + "memorialPhotoUrl·memorialVideo·memorialAudioUrl 은 필드 생략 시 유지, "
                        + "JSON null 이면 해당 미디어를 삭제한다(DB 참조 제거 + S3 객체 삭제). "
                        + "값이 있으면 교체(업로드로 발급된 afternotes 키만 허용)."
        );
        documentOptionalRequestSongs(schemas.get("PlaylistRequest"));

        replaceUnionSchema(schemas);
        ensureAlwaysRequired(schemas);
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
    static void documentOptionalRequestSongs(Schema<?> playlistRequest) {
        if (playlistRequest == null) {
            return;
        }
        if (playlistRequest.getRequired() != null) {
            playlistRequest.getRequired().remove("songs");
        }
        if (playlistRequest.getProperties() == null) {
            return;
        }
        Schema songs = (Schema) playlistRequest.getProperties().get("songs");
        if (songs == null) {
            return;
        }
        songs.setNullable(true);
        songs.setMinItems(null);
        songs.setDescription(
                "노래 목록. PATCH: 필드 생략(null) 시 기존 곡 유지, 빈 배열 [] 은 전부 삭제(발행 노트는 1610). "
                        + "생성 정식 등록이면 1곡 이상 필수."
        );
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
