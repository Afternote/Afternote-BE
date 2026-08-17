package com.afternote.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

public record UserUpdateMarketingConsentRequest(
        @Schema(description = "마케팅 문자(SMS) 수신 동의", example = "false")
        @Getter
        Boolean sms,

        @Schema(description = "마케팅 이메일 수신 동의. 가입·인증·수신자 안내 메일은 이 값과 무관", example = "true")
        @Getter
        Boolean email,

        @Schema(description = "마케팅 푸시 수신 동의. 서비스 알림 3종과 별개", example = "false")
        @Getter
        Boolean push
) {
}
