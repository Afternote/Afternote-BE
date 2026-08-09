package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Schema(description = "수신자 파일 업로드용 Presigned URL 요청")
public record ReceiverPresignedUrlRequest(
        @Schema(description = "파일 확장자 (점 없이)", example = "pdf",
            allowableValues = {"jpg", "jpeg", "png", "gif", "webp", "heic", "pdf"})
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
