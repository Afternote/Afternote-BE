package com.afternote.domain.music.dto;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

public record GetMusicSearchResponse(
        @Schema(description = "검색된 노래 목록")
        @Getter
        List<MusicSearchItemDto> tracks
) {

}
