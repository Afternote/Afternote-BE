package com.afternote.domain.timeletter.dto.request;

import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "타임레터 수정 요청")
public record TimeLetterUpdateRequest(
        @Schema(description = "제목", example = "미래의 나에게")
        @Getter
        String title,

        @Schema(
                type = "string",
                format = "date-time",
                description = "발송 예정 시간 (DATE 모드 전용, UTC 오프셋 포함)",
                example = "2025-12-31T23:59:59+09:00"
        )
        @Getter
        OffsetDateTime sendAt,

        @Schema(description = "전달 방식 (DATE: 날짜 기반, POST_DEATH: 사후 전달)", example = "DATE")
        @Getter
        TimeLetterDeliveryMode deliveryMode,

        @Schema(description = "상태 (DRAFT: 임시저장, SCHEDULED: 정식등록)")
        @Getter
        TimeLetterStatus status,

        @Schema(description = "본문 블록 목록")
        @Valid
        @Getter
        List<TimeLetterBlockRequest> blocks
) {





}
