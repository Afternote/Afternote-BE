package com.afternote.domain.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

public record PresignedUrlRequest(
        @Schema(description = "업로드 디렉토리", example = "profiles",
            allowableValues = {"profiles", "timeletters", "afternotes", "mindrecords", "documents"})
        @NotBlank(message = "디렉토리는 필수입니다.")
        @Getter
        String directory,

        @Schema(description = "파일 확장자 (점 없이)", example = "jpg",
            allowableValues = {"jpg", "jpeg", "png", "gif", "webp", "heic", "mp4", "mov", "mp3", "m4a", "wav", "pdf"})
        @NotBlank(message = "파일 확장자는 필수입니다.")
        @Getter
        String extension,

        @Schema(description = "업로드할 파일 크기(바이트). Presigned PUT 시 Content-Length 와 동일해야 합니다.",
            example = "1048576")
        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 1바이트 이상이어야 합니다.")
        @Getter
        Long contentLength
) {
}
