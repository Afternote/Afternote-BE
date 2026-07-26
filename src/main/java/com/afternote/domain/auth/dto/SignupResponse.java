package com.afternote.domain.auth.dto;

import com.afternote.domain.user.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;


@Builder
public record SignupResponse(
        @Schema(description = "가입된 유저 ID", example = "1")
        @Getter
        Long userId,

        @Schema(description = "가입된 이메일", example = "user@example.com")
        @Getter
        String email
) {



    public static SignupResponse from(User user) {
        return SignupResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .build();
    }

}
