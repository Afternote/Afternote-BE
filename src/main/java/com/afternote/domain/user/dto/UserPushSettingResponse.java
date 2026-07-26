package com.afternote.domain.user.dto;

import com.afternote.domain.user.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
public record UserPushSettingResponse(
        @Schema(description = "타임레터 푸시 알림 수신 여부", example = "true")
        @Getter
        Boolean timeLetter,

        @Schema(description = "마음의 기록 푸시 알림 수신 여부", example = "false")
        @Getter
        Boolean mindRecord,

        @Schema(description = "애프터노트 푸시 알림 수신 여부", example = "true")
        @Getter
        Boolean afterNote
) {




    public static UserPushSettingResponse from(User user) {
        return UserPushSettingResponse.builder()
                .timeLetter(user.isTimeLetterPushEnabled())
                .mindRecord(user.isMindRecordPushEnabled())
                .afterNote(user.isAfterNotePushEnabled())
                .build();
    }
}