package com.afternote.domain.afternote.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

@Schema(description = "발행 완료 PLAYLIST의 플레이리스트. songs는 최소 1곡")
public record AfternotePublishedPlaylistResponse(
        @Schema(description = "분위기 설명", example = "차분하고 조용하게 보내주세요.")
        @Getter
        String atmosphere,

        @Schema(description = "영정 사진 URL", nullable = true)
        @Getter
        String memorialPhotoUrl,

        @ArraySchema(minItems = 1)
        @Schema(
                description = "노래 목록. 발행 완료 PLAYLIST는 최소 1곡",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        List<AfternoteCreateRequest.SongRequest> songs,

        @Schema(
                description = "추모 영상",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                allOf = AfternoteCreateRequest.MemorialVideoRequest.class
        )
        @Getter
        AfternoteCreateRequest.MemorialVideoRequest memorialVideo,

        @Schema(description = "추모 음성 URL", nullable = true)
        @Getter
        String memorialAudioUrl
) {

    public static AfternotePublishedPlaylistResponse from(AfternoteCreateRequest.PlaylistRequest playlist) {
        return new AfternotePublishedPlaylistResponse(
                playlist.getAtmosphere(),
                playlist.getMemorialPhotoUrl(),
                playlist.getSongs(),
                playlist.getMemorialVideo(),
                playlist.getMemorialAudioUrl()
        );
    }
}
