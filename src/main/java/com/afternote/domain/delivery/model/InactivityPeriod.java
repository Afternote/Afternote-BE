package com.afternote.domain.delivery.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "미사용 자동 전달 기간")
public enum InactivityPeriod {
    @Schema(description = "3개월")
    THREE_MONTHS(90),
    @Schema(description = "6개월")
    SIX_MONTHS(180),
    @Schema(description = "1년")
    ONE_YEAR(365);

    private final int days;

    InactivityPeriod(int days) {
        this.days = days;
    }
}
