package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

public record SignupRequest(
        @Schema(description = "이메일 주소", example = "user@example.com")
        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Getter
        String email,

        @Schema(description = "비밀번호", example = "password123!")
        @Pattern(regexp = PasswordValidation.REGEX, message = PasswordValidation.MESSAGE)
        @NotBlank(message = "비밀번호를 입력해주세요.")
        @Getter
        String password,

        @Schema(description = "이름", example = "황규운")
        @NotBlank(message = "이름을 입력해주세요")
        @Getter
        String name,

        @Schema(description = "프로필 사진", example = "https://s3.~~")
        @Getter
        String profileUrl
) {




}
