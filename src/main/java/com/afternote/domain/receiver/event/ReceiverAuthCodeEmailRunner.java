package com.afternote.domain.receiver.event;

import com.afternote.domain.receiver.service.AuthCodeMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiverAuthCodeEmailRunner {

    private final AuthCodeMessageService authCodeMessageService;
    private final StringRedisTemplate stringRedisTemplate;

    @Async
    public void send(ReceiverAuthCodeEmailRequestedEvent event) {
        send(
                event.email(),
                event.authCode(),
                event.senderName(),
                event.receiverName(),
                event.receiverId(),
                null
        );
    }

    /**
     * 수신자 이메일 인증코드 재발송용. SMTP 실패 시 Redis 키를 지워 재요청 가능하게 한다.
     */
    @Async
    public void sendEmailAuthCode(
            String email,
            String authCode,
            String senderName,
            String receiverName,
            Long receiverId,
            String redisKeyToDeleteOnFailure
    ) {
        send(email, authCode, senderName, receiverName, receiverId, redisKeyToDeleteOnFailure);
    }

    private void send(
            String email,
            String authCode,
            String senderName,
            String receiverName,
            Long receiverId,
            String redisKeyToDeleteOnFailure
    ) {
        try {
            authCodeMessageService.sendAuthCode(email, authCode, senderName, receiverName);
        } catch (Exception e) {
            log.warn(
                    "Failed to send auth code via email for receiver {}: {}",
                    receiverId,
                    e.getMessage()
            );
            if (StringUtils.hasText(redisKeyToDeleteOnFailure)) {
                stringRedisTemplate.delete(redisKeyToDeleteOnFailure);
            }
        }
    }
}
