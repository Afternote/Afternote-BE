package com.afternote.domain.user.dto;

import com.afternote.domain.user.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
public record UserMarketingConsentResponse(
        @Schema(description = "마케팅 문자(SMS) 수신 동의", example = "false")
        @Getter
        Boolean sms,

        @Schema(description = "마케팅 이메일 수신 동의", example = "false")
        @Getter
        Boolean email,

        @Schema(description = "마케팅 푸시 수신 동의", example = "false")
        @Getter
        Boolean push
) {

    public static UserMarketingConsentResponse from(User user) {
        return UserMarketingConsentResponse.builder()
                .sms(user.isMarketingSmsEnabled())
                .email(user.isMarketingEmailEnabled())
                .push(user.isMarketingPushEnabled())
                .build();
    }
}
