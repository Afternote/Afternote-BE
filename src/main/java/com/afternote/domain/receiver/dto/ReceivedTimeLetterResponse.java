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
        @Schema(description = "타임레터 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long id,

        @Schema(description = "수신 타임레터 ID (상세 조회 시 사용)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long timeLetterReceiverId,

        @Schema(
                description = TITLE_DESCRIPTION,
                example = "미래의 나에게",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        String title,

        @Schema(description = "본문 블록 목록. 발송 전이면 빈 배열", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        List<TimeLetterBlockResponse> blocks,

        @Schema(
                description = SEND_AT_DESCRIPTION,
                example = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        LocalDateTime sendAt,

        @Schema(description = "상태", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        TimeLetterStatus status,

        @Schema(
                description = SENDER_NAME_DESCRIPTION,
                example = "김철수",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        String senderName,

        @Schema(
                description = "배달 시간. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)",
                example = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        LocalDateTime deliveredAt,

        @Schema(
                description = CREATED_AT_DESCRIPTION,
                example = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        LocalDateTime createdAt,

        @Schema(
                description = IS_READ_DESCRIPTION,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        Boolean isRead
) {
    public static final String TITLE_DESCRIPTION =
            "제목. DATE 타임레터는 sendAt이 지나기 전이면 null. POST_DEATH는 수신 경로에 올리면 공개";
    public static final String SEND_AT_DESCRIPTION =
            "발송 예정 시간. POST_DEATH이거나 미설정이면 null. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)";
    public static final String SENDER_NAME_DESCRIPTION =
            "발신자 이름. DATE 타임레터는 sendAt이 지나기 전이면 null";
    public static final String CREATED_AT_DESCRIPTION =
            "작성 시간. DATE 타임레터는 sendAt이 지나기 전이면 null. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)";
    public static final String IS_READ_DESCRIPTION =
            "읽음 여부. DATE 타임레터는 sendAt이 지나기 전이면 null";

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
