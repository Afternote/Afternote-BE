package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.timeletter.dto.response.TimeLetterBlockResponse;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

@Schema(description = "수신한 타임레터 응답")
@Builder
public record ReceivedTimeLetterResponse(
        @Schema(description = "타임레터 ID", example = "1")
        @Getter
        Long id,

        @Schema(description = "수신 타임레터 ID (상세 조회 시 사용)", example = "1")
        @Getter
        Long timeLetterReceiverId,

        @Schema(description = "제목", example = "미래의 나에게")
        @Getter
        String title,

        @Schema(description = "본문 블록 목록")
        @Getter
        List<TimeLetterBlockResponse> blocks,

        @Schema(description = "발송 예정 시간")
        @Getter
        LocalDateTime sendAt,

        @Schema(description = "상태")
        @Getter
        TimeLetterStatus status,

        @Schema(description = "발신자 이름", example = "김철수")
        @Getter
        String senderName,

        @Schema(description = "배달 시간")
        @Getter
        LocalDateTime deliveredAt,

        @Schema(description = "작성 시간")
        @Getter
        LocalDateTime createdAt,

        @Schema(description = "읽음 여부")
        @Getter
        Boolean isRead
) {











    public static ReceivedTimeLetterResponse from(TimeLetterReceiver timeLetterReceiver) {
        return from(timeLetterReceiver, null);
    }

    public static ReceivedTimeLetterResponse from(
            TimeLetterReceiver timeLetterReceiver,
            Function<String, String> urlResolver
    ) {
        TimeLetter timeLetter = timeLetterReceiver.getTimeLetter();

        // DATE: sendAt이 지난 뒤에만 본문 공개. POST_DEATH: 수신 경로에 올렸으면 조건 충족과 같으므로 공개.
        boolean isAvailable = timeLetter.isPostDeath()
                || (timeLetter.getSendAt() != null
                && !timeLetter.getSendAt().isAfter(LocalDateTime.now()));

        List<TimeLetterBlockResponse> blockResponses = isAvailable
                ? timeLetter.getBlocks().stream()
                .map(block -> TimeLetterBlockResponse.from(block, urlResolver))
                .toList()
                : List.of();

        return ReceivedTimeLetterResponse.builder()
                .id(timeLetter.getId())
                .timeLetterReceiverId(timeLetterReceiver.getId())
                .title(isAvailable ? timeLetter.getTitle() : null)
                .blocks(blockResponses)
                .sendAt(timeLetter.getSendAt())
                .status(timeLetter.getStatus())
                .senderName(isAvailable ? timeLetter.getUser().getName() : null)
                .deliveredAt(timeLetterReceiver.getDeliveredAt())
                .createdAt(isAvailable ? timeLetter.getCreatedAt() : null)
                .isRead(isAvailable ? (timeLetterReceiver.getReadAt() != null) : null)
                .build();
    }
}