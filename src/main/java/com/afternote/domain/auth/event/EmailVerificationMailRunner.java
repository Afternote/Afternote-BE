package com.afternote.domain.auth.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 회원가입/계정찾기 인증메일 비동기 발송.
 * 실패 시 코드·쿨다운을 지워 재요청 가능하게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationMailRunner {

    private final JavaMailSender javaMailSender;
    private final RedisTemplate<String, String> redisTemplate;

    @Async
    public void send(SimpleMailMessage message, String codeKey, String cooldownKey) {
        try {
            javaMailSender.send(message);
        } catch (Exception e) {
            String to = message.getTo() != null && message.getTo().length > 0
                    ? message.getTo()[0]
                    : "unknown";
            log.warn("Failed to send verification email to {}: {}", to, e.getMessage());
            redisTemplate.delete(codeKey);
            redisTemplate.delete(cooldownKey);
        }
    }
}
