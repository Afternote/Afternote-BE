package com.afternote.domain.afternote.dto;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "애프터노트 상세 응답")
public record AfternotedetailResponse(
        @Schema(description = "애프터노트 아이디", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Long afternoteId,

        @Schema(description = "카테고리", example = "SOCIAL", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        AfternoteCategoryType category,

        @Schema(description = "제목", example = "인스타그램", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        String title,

        @Schema(description = "임시저장 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        Boolean isDraft,

        @Schema(
                description = "체크리스트 (SOCIAL/BUSINESS/GALLERY 전용). PLAYLIST는 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        List<String> actions,

        @Schema(
                description = "남기실 말씀 블록 목록 (제목+본문). 미작성·GALLERY 임시저장 등 일부 경우 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        List<LeaveMessageBlock> leaveMessage,

        @Schema(
                description = "계정 정보 (SOCIAL/BUSINESS 전용). GALLERY/PLAYLIST는 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        AfternoteCreateRequest.CredentialsRequest credentials,

        @Schema(description = "수신자 목록 (receiverId, name, relation)", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        List<AfternoteReceiverResponse> receivers,

        @Schema(
                description = "플레이리스트 정보 (PLAYLIST 전용). 그 외 카테고리는 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Getter
        AfternoteCreateRequest.PlaylistRequest playlist,

        @Schema(description = "최종 수정일", requiredMode = Schema.RequiredMode.REQUIRED)
        @Getter
        LocalDateTime updatedAt
) {
}
