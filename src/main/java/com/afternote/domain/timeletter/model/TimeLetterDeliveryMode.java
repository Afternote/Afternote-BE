package com.afternote.domain.timeletter.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "타임레터 전달 방식")
public enum TimeLetterDeliveryMode {
    @Schema(description = "날짜 기반 - 지정한 날짜에 전달 (생전/사후 무관)")
    DATE,
    @Schema(description = "사후 전달 - 발신자 사망(전달 조건 충족) 후 전달")
    POST_DEATH
}
