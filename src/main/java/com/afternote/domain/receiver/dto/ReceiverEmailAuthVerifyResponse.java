package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.Receiver;
import lombok.Getter;

public record ReceiverEmailAuthVerifyResponse(
        @Getter
        Long receiverId,

        @Getter
        String receiverName,

        @Getter
        String senderName,

        @Getter
        String accessCode
) {


    /**
     * 이후 X-Auth-Code 헤더에 넣을 UUID 접근 코드
     */

    public static ReceiverEmailAuthVerifyResponse from(Receiver receiver, String senderName) {
        return new ReceiverEmailAuthVerifyResponse(
                receiver.getId(),
                receiver.getName(),
                senderName,
                receiver.getAuthCode()
        );
    }
}