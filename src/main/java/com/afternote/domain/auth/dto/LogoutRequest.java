package com.afternote.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;


public record LogoutRequest(
        @NotBlank(message = "리프레쉬 토큰을 보내주십시오")
        @Schema(description = "리프레쉬 토큰" ,example = "safasfskldfjasdkgwr34tklgfdldfsdckl...")
        @Getter
        String refreshToken
) {


}
