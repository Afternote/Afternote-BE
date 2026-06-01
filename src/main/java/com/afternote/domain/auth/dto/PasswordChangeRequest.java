package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Getter
@NoArgsConstructor
public class PasswordChangeRequest {

    @Schema(description = "현재 비밀번호", example = "password123!")
    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    private String currentPassword;


    @Schema(description = "새 비밀번호", example = "password123!")
    @Pattern(regexp = PasswordValidation.REGEX, message = PasswordValidation.MESSAGE)
    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    private String newPassword;

}
