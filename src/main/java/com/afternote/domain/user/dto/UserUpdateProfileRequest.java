package com.afternote.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public record UserUpdateProfileRequest(
        @Schema(description = "사용자 이름", example = "김소희", nullable = true)
        @Getter
        String name,

        @Schema(description = "연락처", example = "01012345678", nullable = true)
        @Getter
        String phone,

        @Schema(description = "프로필 이미지 URL",
            example = "https://cdn.example.com/profile/1.png", nullable = true)
        @Getter
        String profileImageUrl
) {
}
