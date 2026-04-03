package com.example.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "수신자 파일 업로드용 Presigned URL 요청")
public class ReceiverPresignedUrlRequest {

    @Schema(description = "파일 확장자 (점 없이)", example = "pdf",
            allowableValues = {"jpg", "jpeg", "png", "gif", "webp", "heic", "pdf"})
    @NotBlank(message = "파일 확장자는 필수입니다.")
    private String extension;
}
