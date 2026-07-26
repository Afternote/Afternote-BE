package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

@Schema(description = "다이어리 수신자 등록 요청")
public record CreateDiaryReceiverRequest(
        @NotNull(message = "다이어리 ID는 필수입니다.")
        @Schema(description = "다이어리 ID", example = "1")
        @Getter
        Long diaryId,

        @NotEmpty(message = "수신자 ID 목록은 필수입니다.")
        @Schema(description = "수신자 ID 목록", example = "[1, 2, 3]")
        @Getter
        List<Long> receiverIds
) {


}
