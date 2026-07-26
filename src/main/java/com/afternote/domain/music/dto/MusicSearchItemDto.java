package com.afternote.domain.music.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public record MusicSearchItemDto(
        @Schema(description = "가수 이름", example = "IU")
        @Getter
        String artist,

        @Schema(description = "노래 제목", example = "좋은날")
        @Getter
        String title,

        @Schema(description = "앨범 이미지 URL",
            example = "https://is1-ssl.mzstatic.com/image/thumb/Music126/v4/.../100x100bb.jpg",
            nullable = true)
        @Getter
        String albumImageUrl
) {
}
