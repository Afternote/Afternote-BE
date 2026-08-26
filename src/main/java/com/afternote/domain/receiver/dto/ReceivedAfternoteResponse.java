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
        @Schema(description = "애프터노트 ID", example = "1")
        @Getter
        Long id,

        @Schema(description = "제목", example = "내 아들에게")
        @Getter
        String title,

        @Schema(description = "카테고리", example = "GALLERY", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        AfternoteCategoryType category,

        @Schema(description = "남기실 말씀 블록 목록 (제목+본문)")
        @Getter
        List<LeaveMessageBlock> leaveMessage,

        @Schema(description = "발신자 ID", example = "1")
        @Getter
        Long senderId,

        @Schema(description = "발신자 이름", example = "김철수")
        @Getter
        String senderName,

        @Schema(description = "작성 시간")
        @Getter
        LocalDateTime createdAt
) {








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
