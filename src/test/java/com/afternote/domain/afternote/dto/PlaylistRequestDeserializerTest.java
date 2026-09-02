package com.afternote.domain.afternote.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class PlaylistRequestDeserializerTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    @DisplayName("PATCH에서 미디어 필드 생략은 specified=false (유지)")
    void omittedMediaFields_AreUnspecified() throws Exception {
        AfternoteUpdateRequest request = objectMapper.readValue(
                "{\"playlist\":{\"atmosphere\":\"차분\"}}",
                AfternoteUpdateRequest.class
        );

        AfternoteCreateRequest.PlaylistRequest playlist = request.getPlaylist();
        assertThat(playlist.getAtmosphere()).isEqualTo("차분");
        assertThat(playlist.memorialPhotoUrlSpecified()).isFalse();
        assertThat(playlist.memorialVideoSpecified()).isFalse();
        assertThat(playlist.memorialAudioUrlSpecified()).isFalse();
        assertThat(playlist.getMemorialPhotoUrl()).isNull();
        assertThat(playlist.getMemorialAudioUrl()).isNull();
        assertThat(playlist.getSongs()).isNull();
    }

    @Test
    @DisplayName("PATCH에서 JSON null 은 specified=true 이고 값은 null (삭제)")
    void explicitNullMediaFields_AreSpecifiedClear() throws Exception {
        AfternoteUpdateRequest request = objectMapper.readValue(
                """
                {
                  "playlist": {
                    "memorialPhotoUrl": null,
                    "memorialVideo": null,
                    "memorialAudioUrl": null
                  }
                }
                """,
                AfternoteUpdateRequest.class
        );

        AfternoteCreateRequest.PlaylistRequest playlist = request.getPlaylist();
        assertThat(playlist.memorialPhotoUrlSpecified()).isTrue();
        assertThat(playlist.memorialVideoSpecified()).isTrue();
        assertThat(playlist.memorialAudioUrlSpecified()).isTrue();
        assertThat(playlist.getMemorialPhotoUrl()).isNull();
        assertThat(playlist.getMemorialVideo()).isNull();
        assertThat(playlist.getMemorialAudioUrl()).isNull();
    }

    @Test
    @DisplayName("PATCH에서 값이 있으면 specified=true")
    void presentMediaFields_AreSpecified() throws Exception {
        AfternoteUpdateRequest request = objectMapper.readValue(
                """
                {
                  "playlist": {
                    "memorialPhotoUrl": "afternotes/staging/1/a.jpg",
                    "memorialAudioUrl": "afternotes/staging/1/a.m4a"
                  }
                }
                """,
                AfternoteUpdateRequest.class
        );

        AfternoteCreateRequest.PlaylistRequest playlist = request.getPlaylist();
        assertThat(playlist.memorialPhotoUrlSpecified()).isTrue();
        assertThat(playlist.memorialAudioUrlSpecified()).isTrue();
        assertThat(playlist.getMemorialPhotoUrl()).isEqualTo("afternotes/staging/1/a.jpg");
        assertThat(playlist.getMemorialAudioUrl()).isEqualTo("afternotes/staging/1/a.m4a");
        assertThat(playlist.memorialVideoSpecified()).isFalse();
        assertThat(playlist.getSongs()).isNull();
    }

    @Test
    @DisplayName("PATCH에서 songs 빈 배열은 빈 리스트(전부 삭제)")
    void emptySongsArray_IsEmptyList() throws Exception {
        AfternoteUpdateRequest request = objectMapper.readValue(
                "{\"playlist\":{\"songs\":[]}}",
                AfternoteUpdateRequest.class
        );

        assertThat(request.getPlaylist().getSongs()).isEmpty();
    }
}
