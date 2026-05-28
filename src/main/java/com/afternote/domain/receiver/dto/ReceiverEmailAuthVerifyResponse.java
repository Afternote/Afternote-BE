package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.Receiver;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReceiverEmailAuthVerifyResponse {

    private Long receiverId;
    private String receiverName;
    private String senderName;

    /**
     * 이후 X-Auth-Code 헤더에 넣을 UUID 접근 코드
     */
    private String accessCode;

    public static ReceiverEmailAuthVerifyResponse from(Receiver receiver, String senderName) {
        return new ReceiverEmailAuthVerifyResponse(
                receiver.getId(),
                receiver.getName(),
                senderName,
                receiver.getAuthCode()
        );
    }
}