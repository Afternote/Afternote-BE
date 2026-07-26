package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public record EmailVerifyRequest(
        @Schema(description = "이메일 주소", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Getter
        String email,

        @Schema(description = "이메일 주소", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Getter
        String certificateCode
) {


}
