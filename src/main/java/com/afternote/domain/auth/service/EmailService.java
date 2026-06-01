package com.afternote.domain.auth.service;

import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String CODE_KEY_PREFIX = "EMAIL:";
    private static final String COOLDOWN_KEY_PREFIX = "EMAIL_COOLDOWN:";
    private static final String HOURLY_COUNT_KEY_PREFIX = "EMAIL_SEND_COUNT:";
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(10);
    private static final Duration HOURLY_LIMIT_WINDOW = Duration.ofHours(1);
    private static final long MAX_SENDS_PER_HOUR = 5L;

    private final JavaMailSender javaMailSender;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendCode(String toEmail, EmailVerificationPurpose purpose) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + purpose.name() + ":" + toEmail;
        String hourlyKey = HOURLY_COUNT_KEY_PREFIX + toEmail;

        String currentCountStr = redisTemplate.opsForValue().get(hourlyKey);
        long currentCount = currentCountStr == null ? 0L : Long.parseLong(currentCountStr);
        if (currentCount >= MAX_SENDS_PER_HOUR) {
            throw new CustomException(ErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
        }

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", RESEND_COOLDOWN);
        if (Boolean.FALSE.equals(acquired)) {
            throw new CustomException(ErrorCode.EMAIL_SEND_COOLDOWN);
        }

        String authCode = createCode();
        String codeKey = buildCodeKey(toEmail, purpose);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[AfterNote] 이메일 인증 번호입니다.");
        message.setText("인증 번호는 [" + authCode + "] 입니다. 3분 안에 입력해주세요.");
        message.setFrom(senderEmail);

        try {
            javaMailSender.send(message);
        } catch (RuntimeException e) {
            redisTemplate.delete(cooldownKey);
            throw e;
        }

        redisTemplate.opsForValue().set(codeKey, authCode, 3, TimeUnit.MINUTES);

        Long newCount = redisTemplate.opsForValue().increment(hourlyKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(hourlyKey, HOURLY_LIMIT_WINDOW);
        }
    }

    public boolean verifyCode(String email, String inputCode, EmailVerificationPurpose purpose) {
        String redisCode = redisTemplate.opsForValue().get(buildCodeKey(email, purpose));
        return redisCode != null && redisCode.equals(inputCode);
    }

    public boolean verifyAndDeleteCode(String email, String inputCode, EmailVerificationPurpose purpose) {
        String codeKey = buildCodeKey(email, purpose);
        String redisCode = redisTemplate.opsForValue().get(codeKey);

        if (redisCode == null || !redisCode.equals(inputCode)) {
            return false;
        }

        redisTemplate.delete(codeKey);
        return true;
    }

    private String buildCodeKey(String email, EmailVerificationPurpose purpose) {
        return CODE_KEY_PREFIX + purpose.name() + ":" + email;
    }

    private String createCode() {
        Random random = new Random();
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            key.append(random.nextInt(10));
        }
        return key.toString();
    }
}
