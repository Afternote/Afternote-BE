package com.afternote.domain.timeletter.dto.request;

import com.afternote.domain.timeletter.model.TimeLetterBlockType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Schema(description = "타임레터 본문 블록 요청")
public record TimeLetterBlockRequest(
        @Schema(description = "블록 타입", example = "TEXT")
        @NotNull(message = "블록 타입을 입력해주세요.")
        @Getter
        TimeLetterBlockType blockType,

        @Schema(description = "블록 순서", example = "1")
        @NotNull(message = "블록 순서를 입력해주세요.")
        @Getter
        Integer blockOrder,

        @Schema(description = "텍스트 내용", example = "1년 후의 나에게 보내는 편지...")
        @Getter
        String textContent,

        @Schema(description = "이미지/음성/파일/링크 URL", example = "https://example.com/image.jpg")
        @Getter
        String url,

        @Schema(description = "파일 MIME 타입", example = "application/pdf")
        @Getter
        String mimeType
) {





}