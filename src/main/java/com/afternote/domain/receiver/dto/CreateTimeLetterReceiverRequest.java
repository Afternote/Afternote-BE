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

        @Schema(description = "호환용 입력. 예약은 타임레터 sendAt을 사용하고 실제 전달 시각은 서버가 판정하므로 이 값은 전달 결과로 저장하지 않습니다.", deprecated = true, example = "2025-12-31T23:59:59")
        @Getter
        LocalDateTime deliveredAt
) {



}
