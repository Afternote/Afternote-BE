package com.afternote.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public record UserPatchReceiverRequest(
        @Schema(description = "이름", example = "김소희")
        @NotBlank
        @Getter
        String name,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @NotBlank
        @Getter
        String phone,

        @Schema(description = "관계", example = "DAUGHTER")
        @NotBlank
        @Getter
        String relation,

        @Schema(description = "이메일", example = "sohee@example.com")
        @NotBlank
        @Email
        @Getter
        String email
) {





}
