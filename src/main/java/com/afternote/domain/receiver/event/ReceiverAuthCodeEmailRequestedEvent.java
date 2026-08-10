package com.afternote.domain.receiver.event;

public record ReceiverAuthCodeEmailRequestedEvent(
        Long receiverId,
        String email,
        String authCode,
        String senderName,
        String receiverName
) {}
