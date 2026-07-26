package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

public record EmailFindRequest(
        @Schema(description = "가입 시 사용한 이메일", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Getter
        String email,

        @Schema(description = "이메일 인증번호 (6자리)", example = "123456")
        @NotBlank(message = "인증번호를 입력해주세요.")
        @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 6자리 숫자여야 합니다.")
        @Getter
        String certificateCode
) {


}
