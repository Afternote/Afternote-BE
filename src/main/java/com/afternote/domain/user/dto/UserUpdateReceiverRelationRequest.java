package com.afternote.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UserUpdateReceiverRelationRequest(
        @Schema(description = "수신자와의 관계", example = "친구")
        @NotBlank(message = "수신자와의 관계를 입력해주세요.")
        String relation
) {
}
