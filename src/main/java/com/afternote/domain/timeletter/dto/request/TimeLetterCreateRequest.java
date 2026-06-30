package com.afternote.domain.timeletter.dto.request;

import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "타임레터 생성 요청")
@Getter
@NoArgsConstructor
public class TimeLetterCreateRequest {

    @Schema(description = "제목", example = "미래의 나에게")
    private String title;

    @Schema(description = "발송 예정 시간 (DATE 모드에서만 사용)", example = "2025-12-31T23:59:59")
    private LocalDateTime sendAt;

    @Schema(description = "전달 방식 (DATE: 날짜 기반, POST_DEATH: 사후 전달). 미지정 시 DATE", example = "DATE")
    private TimeLetterDeliveryMode deliveryMode;

    @Schema(description = "상태 (DRAFT: 임시저장, SCHEDULED: 정식등록)")
    @NotNull(message = "상태를 입력해주세요.")
    private TimeLetterStatus status;

    @Schema(description = "본문 블록 목록")
    @Valid
    private List<TimeLetterBlockRequest> blocks;

    @Schema(description = "수신자 ID 목록", example = "[1, 2, 3]")
    @NotEmpty(message = "수신자를 선택해주세요.")
    private List<Long> receiverIds;
}