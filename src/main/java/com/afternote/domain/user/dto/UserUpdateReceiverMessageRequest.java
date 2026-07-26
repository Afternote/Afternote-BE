package com.afternote.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

public record UserUpdateReceiverMessageRequest(
        @Schema(description = "수신자에게 남길 메시지", example = "사랑하는 딸에게...")
        @NotNull
        @Getter
        String message
) {

}
