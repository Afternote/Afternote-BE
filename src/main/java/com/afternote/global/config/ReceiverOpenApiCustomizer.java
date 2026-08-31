package com.afternote.global.config;

import com.afternote.domain.receiver.dto.ReceivedAfternoteDetailResponse;
import com.afternote.domain.receiver.dto.ReceivedRecordBoxResponse;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;

import java.util.Map;

/**
 * 수신자 응답 OpenAPI를 실계약에 맞춘다.
 * LocalDateTime은 Jackson이 오프셋 없이 쓰는데 springdoc가 format=date-time으로 덮으므로 되돌린다.
 * 객체 필드의 $ref/oneOf 형제 nullable은 allOf로 남긴다.
 */
public class ReceiverOpenApiCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();

        AfternoteDetailOpenApiCustomizer.flattenSelfAllOf(schemas.get("ReceivedPlaylistInfo"));
        AfternoteDetailOpenApiCustomizer.flattenSelfAllOf(schemas.get("ReceivedMemorialVideoInfo"));

        wrapNullableObject(
                schemas.get("ReceivedAfternoteDetailResponse"),
                "playlist",
                ReceivedAfternoteDetailResponse.PLAYLIST_DESCRIPTION
        );
        wrapNullableObject(
                schemas.get("ReceivedPlaylistInfo"),
                "memorialVideo",
                "추모 영상. 없으면 null"
        );

        rewriteLocalDateTimeFormats(schemas);
    }

    @SuppressWarnings("rawtypes")
    private static void rewriteLocalDateTimeFormats(Map<String, Schema> schemas) {
        for (Schema<?> schema : schemas.values()) {
            if (schema == null || schema.getProperties() == null) {
                continue;
            }
            for (Object raw : schema.getProperties().values()) {
                if (!(raw instanceof Schema<?> property)) {
                    continue;
                }
                String description = property.getDescription();
                if (description == null || !description.contains("오프셋 없는 ISO-8601")) {
                    continue;
                }
                property.setFormat(ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT);
                property.setExample(ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE);
                property.setExampleSetFlag(true);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void wrapNullableObject(Schema<?> parent, String propertyName, String description) {
        if (parent == null || parent.getProperties() == null) {
            return;
        }
        Schema property = (Schema) parent.getProperties().get(propertyName);
        if (property == null) {
            return;
        }
        String ref = firstRef(property);
        if (ref == null) {
            AfternoteDetailOpenApiCustomizer.wrapNullableRef(parent, propertyName, description);
            return;
        }
        ComposedSchema wrapped = new ComposedSchema();
        wrapped.setNullable(true);
        wrapped.setDescription(description);
        wrapped.addAllOfItem(new Schema<>().$ref(ref));
        parent.getProperties().put(propertyName, wrapped);
        if (parent.getRequired() != null) {
            parent.getRequired().remove(propertyName);
        }
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
        if (property.getOneOf() != null) {
            for (Object item : property.getOneOf()) {
                if (item instanceof Schema<?> schema && schema.get$ref() != null) {
                    return schema.get$ref();
                }
            }
        }
        return null;
    }
}
