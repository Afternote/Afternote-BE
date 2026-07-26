package com.afternote.domain.timeletter.dto;

import com.afternote.domain.timeletter.model.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Schema(description = "타임레터 미디어 요청")
public record TimeLetterMediaRequest(
        @Schema(description = "미디어 타입", example = "IMAGE")
        @NotNull(message = "미디어 타입을 입력해주세요.")
        @Getter
        MediaType mediaType,

        @Schema(description = "미디어 URL", example = "https://example.com/image.jpg")
        @NotBlank(message = "미디어 URL을 입력해주세요.")
        @Getter
        String mediaUrl
) {


}
