package com.afternote.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Schema(description = "수신자 메시지 수정 요청")
public class UserUpdateReceiverMessageRequest {

    public static final String MESSAGE_DESCRIPTION =
            "수신자에게 남길 메시지. 필드를 생략하면 기존 값을 유지한다. "
                    + "null 또는 공백 문자열이면 메시지를 비워, 상세 조회의 message는 null이 된다.";

    @Schema(
            description = MESSAGE_DESCRIPTION,
            example = "사랑하는 딸에게...",
            nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Getter
    private String message;

    private boolean messageSpecified;

    @JsonProperty("message")
    public void setMessage(String message) {
        this.messageSpecified = true;
        this.message = normalize(message);
    }

    @JsonIgnore
    @Schema(hidden = true)
    public boolean isMessageSpecified() {
        return messageSpecified;
    }

    private static String normalize(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message;
    }
}
