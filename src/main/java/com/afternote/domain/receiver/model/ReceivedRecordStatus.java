package com.afternote.domain.receiver.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "받은 기록함 보관 상태")
public enum ReceivedRecordStatus {

    @Schema(description = "보관 중")
    STORED,

    @Schema(description = "보관된 기록 없음")
    EMPTY,

    @Schema(description = "삭제됨")
    DELETED
}