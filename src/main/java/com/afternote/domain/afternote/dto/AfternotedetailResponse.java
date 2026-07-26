package com.afternote.domain.afternote.dto;

import com.afternote.domain.afternote.model.AfternoteCategoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

public record AfternotedetailResponse(
        @Schema(description = "애프터노트 아이디", example = "1")
        @Getter
        Long afternoteId,

        @Schema(description = "카테고리", example = "SOCIAL")
        @Getter
        AfternoteCategoryType category,

        @Schema(description = "제목", example = "인스타그램")
        @Getter
        String title,

        @Schema(description = "체크리스트 (SOCIAL/GALLERY 전용)")
        @Getter
        List<String> actions,

        @Schema(description = "남기신 말씀 (SOCIAL/GALLERY 전용)")
        @Getter
        String leaveMessage,

        @Schema(description = "계정 정보 (SOCIAL 전용)")
        @Getter
        AfternoteCreateRequest.CredentialsRequest credentials,

        @Schema(description = "수신자 목록")
        @Getter
        List<AfternoteCreateRequest.ReceiverRequest> receivers,

        @Schema(description = "플레이리스트 정보 (Playlist 전용)")
        @Getter
        AfternoteCreateRequest.PlaylistRequest playlist,

        @Schema(description = "최종 수정일")
        @Getter
        LocalDateTime updatedAt
) {









}
