package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "타임레터 수신자 등록 요청")
public record CreateTimeLetterReceiverRequest(
        @NotNull(message = "타임레터 ID는 필수입니다.")
        @Schema(description = "타임레터 ID", example = "1")
        @Getter
        Long timeLetterID,

        @NotEmpty(message = "수신자 ID 목록은 필수입니다.")
        @Schema(description = "수신자 ID 목록", example = "[1, 2, 3]")
        @Getter
        List<Long> receiverIds,

        @Schema(description = "배달 예정 시간", example = "2025-12-31T23:59:59")
        @Getter
        LocalDateTime deliveredAt
) {



}
