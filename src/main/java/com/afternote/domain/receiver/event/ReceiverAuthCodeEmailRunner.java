package com.afternote.domain.receiver.event;

import com.afternote.domain.receiver.service.AuthCodeMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiverAuthCodeEmailRunner {

    private final AuthCodeMessageService authCodeMessageService;

    @Async
    public void send(ReceiverAuthCodeEmailRequestedEvent event) {
        try {
            authCodeMessageService.sendAuthCode(
                    event.email(),
                    event.authCode(),
                    event.senderName(),
                    event.receiverName()
            );
        } catch (Exception e) {
            log.warn(
                    "Failed to send auth code via email for receiver {}: {}",
                    event.receiverId(),
                    e.getMessage()
            );
        }
    }
}
