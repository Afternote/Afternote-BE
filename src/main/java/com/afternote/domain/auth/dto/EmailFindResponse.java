package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public record EmailFindResponse(
        @Schema(description = "회원 이름", example = "박지현")
        @Getter
        String name,

        @Schema(description = "아이디(이메일)", example = "parkchan01@example.com")
        @Getter
        String email
) {



    public static EmailFindResponse from(String name, String email) {
        return new EmailFindResponse(name, email);
    }
}
