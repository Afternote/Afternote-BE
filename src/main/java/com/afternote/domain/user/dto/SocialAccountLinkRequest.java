package com.afternote.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public record SocialAccountLinkRequest(
        @Schema(description = "소셜 제공자에서 발급한 OAuth2 Access Token", example = "ya29.a0AfH6...")
        @NotBlank(message = "Access Token을 입력해주세요.")
        @Getter
        String accessToken
) {

}
