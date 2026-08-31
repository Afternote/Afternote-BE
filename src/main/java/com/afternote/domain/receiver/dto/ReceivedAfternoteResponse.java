package com.afternote.domain.receiver.dto;

import com.afternote.domain.afternote.dto.LeaveMessageBlock;
import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternoteReceiver;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "수신한 애프터노트 응답")
@Builder
public record ReceivedAfternoteResponse(
        @Schema(description = "애프터노트 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long id,

        @Schema(description = "제목", example = "내 아들에게", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String title,

        @Schema(description = "카테고리", example = "GALLERY", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        AfternoteCategoryType category,

        @Schema(
                description = LEAVE_MESSAGE_DESCRIPTION,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        List<LeaveMessageBlock> leaveMessage,

        @Schema(description = "발신자 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long senderId,

        @Schema(description = "발신자 이름", example = "김철수", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String senderName,

        @Schema(
                description = "작성 시간. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)",
                example = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @Getter
        LocalDateTime createdAt
) {
    public static final String LEAVE_MESSAGE_DESCRIPTION =
            "남기실 말씀 블록 목록 (제목+본문). 미작성이면 null";

    public static ReceivedAfternoteResponse from(AfternoteReceiver afternoteReceiver, String senderName) {
        Afternote afternote = afternoteReceiver.getAfternote();
        return ReceivedAfternoteResponse.builder()
                .id(afternote.getId())
                .title(afternote.getTitle())
                .category(afternote.getCategoryType())
                .leaveMessage(afternote.getLeaveMessage())
                .senderId(afternote.getUser().getId())
                .senderName(senderName)
                .createdAt(afternote.getCreatedAt())
                .build();
    }
}
