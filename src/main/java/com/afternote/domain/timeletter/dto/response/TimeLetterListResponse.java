package com.afternote.domain.timeletter.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Schema(description = "타임레터 목록 응답")
@Builder
public record TimeLetterListResponse(
        @Schema(description = "타임레터 목록")
        @Getter
        List<TimeLetterResponse> timeLetters,

        @Schema(description = "총 개수", example = "5")
        @Getter
        int totalCount
) {



    public static TimeLetterListResponse from(List<TimeLetterResponse> timeLetters) {
        return TimeLetterListResponse.builder()
                .timeLetters(timeLetters)
                .totalCount(timeLetters.size())
                .build();
    }
}
