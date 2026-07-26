package com.afternote.domain.timeletter.dto;

import com.afternote.domain.timeletter.model.MediaType;
import com.afternote.domain.timeletter.model.TimeLetterMedia;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.function.Function;

@Schema(description = "타임레터 미디어 응답")
@Builder
public record TimeLetterMediaResponse(
        @Schema(description = "미디어 ID", example = "1")
        @Getter
        Long id,

        @Schema(description = "미디어 타입", example = "IMAGE")
        @Getter
        MediaType mediaType,

        @Schema(description = "미디어 URL", example = "https://example.com/image.jpg")
        @Getter
        String mediaUrl
) {




    public static TimeLetterMediaResponse from(TimeLetterMedia media) {
        return TimeLetterMediaResponse.builder()
                .id(media.getId())
                .mediaType(media.getMediaType())
                .mediaUrl(media.getMediaUrl())
                .build();
    }

    public static TimeLetterMediaResponse from(TimeLetterMedia media, Function<String, String> urlResolver) {
        return TimeLetterMediaResponse.builder()
                .id(media.getId())
                .mediaType(media.getMediaType())
                .mediaUrl(urlResolver.apply(media.getMediaUrl()))
                .build();
    }
}
