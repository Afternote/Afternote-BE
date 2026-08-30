package com.afternote.domain.afternote.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;

import java.io.IOException;
import java.util.List;

/**
 * PATCH에서 미디어 필드 생략(유지)과 JSON null(삭제)을 구분한다.
 */
public class PlaylistRequestDeserializer extends JsonDeserializer<AfternoteCreateRequest.PlaylistRequest> {

    @Override
    public AfternoteCreateRequest.PlaylistRequest deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = p.readValueAsTree();
        if (node == null || node.isNull()) {
            return null;
        }

        String atmosphere = textOrNull(node, "atmosphere");
        boolean memorialPhotoUrlSpecified = node.has("memorialPhotoUrl");
        String memorialPhotoUrl = memorialPhotoUrlSpecified ? textOrNull(node, "memorialPhotoUrl") : null;

        List<AfternoteCreateRequest.SongRequest> songs = null;
        if (node.has("songs") && !node.get("songs").isNull()) {
            JavaType songsType = ctxt.getTypeFactory()
                    .constructCollectionType(List.class, AfternoteCreateRequest.SongRequest.class);
            songs = ctxt.readTreeAsValue(node.get("songs"), songsType);
        }

        boolean memorialVideoSpecified = node.has("memorialVideo");
        AfternoteCreateRequest.MemorialVideoRequest memorialVideo = null;
        if (memorialVideoSpecified && !node.get("memorialVideo").isNull()) {
            memorialVideo = ctxt.readTreeAsValue(node.get("memorialVideo"), AfternoteCreateRequest.MemorialVideoRequest.class);
        }

        boolean memorialAudioUrlSpecified = node.has("memorialAudioUrl");
        String memorialAudioUrl = memorialAudioUrlSpecified ? textOrNull(node, "memorialAudioUrl") : null;

        return AfternoteCreateRequest.PlaylistRequest.parsed(
                atmosphere,
                memorialPhotoUrl,
                songs,
                memorialVideo,
                memorialAudioUrl,
                memorialPhotoUrlSpecified,
                memorialVideoSpecified,
                memorialAudioUrlSpecified
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        String text = child.asText();
        return text == null ? null : text;
    }
}
