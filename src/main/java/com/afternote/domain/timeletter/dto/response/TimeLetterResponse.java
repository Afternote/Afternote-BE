package com.afternote.domain.timeletter.dto.response;

import com.afternote.domain.timeletter.dto.response.TimeLetterBlockResponse;
import com.afternote.domain.timeletter.model.TimeLetter;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Getter
@Builder
@Schema(description = "타임레터 응답")
public class TimeLetterResponse {

    private Long id;
    private String title;
    private LocalDateTime sendAt;
    private LocalDateTime deliveredAt;
    private String status;

    private List<TimeLetterBlockResponse> blocks;

    private List<Long> receiverIds;

    public static TimeLetterResponse from(
            TimeLetter timeLetter,
            Function<String, String> presignedUrlGenerator,
            List<Long> receiverIds
    ) {
        return TimeLetterResponse.builder()
                .id(timeLetter.getId())
                .title(timeLetter.getTitle())
                .sendAt(timeLetter.getSendAt())
                .deliveredAt(timeLetter.getDeliveredAt())
                .status(timeLetter.getStatus().name())
                .blocks(
                        timeLetter.getBlocks().stream()
                                .map(block -> TimeLetterBlockResponse.from(block, presignedUrlGenerator))
                                .toList()
                )
                .receiverIds(receiverIds)
                .build();
    }

    public static TimeLetterResponse from(
            TimeLetter timeLetter,
            Function<String, String> presignedUrlGenerator
    ) {
        return from(timeLetter, presignedUrlGenerator, List.of());
    }
}