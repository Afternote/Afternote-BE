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
    private final RedisTemplate<String, String> redisTemplate; // 이미 설정해둔 RedisTemplate 사용
    
    @Value("${spring.mail.username}")
    private String senderEmail;

    // 1. 인증번호 전송 로직
    public void sendCode(String toEmail) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + toEmail;
        String hourlyKey = HOURLY_COUNT_KEY_PREFIX + toEmail;

        // 시간당 발송 한도 체크 (한도 초과 시 쿨다운 락도 잡지 않음)
        String currentCountStr = redisTemplate.opsForValue().get(hourlyKey);
        long currentCount = currentCountStr == null ? 0L : Long.parseLong(currentCountStr);
        if (currentCount >= MAX_SENDS_PER_HOUR) {
            throw new CustomException(ErrorCode.EMAIL_SEND_LIMIT_EXCEEDED);
        }

        // 재전송 쿨다운 (setIfAbsent로 원자적 체크 & 락)
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", RESEND_COOLDOWN);
        if (Boolean.FALSE.equals(acquired)) {
            throw new CustomException(ErrorCode.EMAIL_SEND_COOLDOWN);
        }

        String authCode = createCode();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[AfterNote] 이메일 인증 번호입니다.");
        message.setText("인증 번호는 [" + authCode + "] 입니다. 3분 안에 입력해주세요.");
        message.setFrom(senderEmail);

        try {
            javaMailSender.send(message);
        } catch (RuntimeException e) {
            // 메일 전송 실패 시 쿨다운 즉시 해제 (사용자 즉시 재시도 허용)
            redisTemplate.delete(cooldownKey);
            throw e;
        }

        // 발송 성공 시에만 인증코드 저장 + 시간당 카운트 증가
        redisTemplate.opsForValue().set(CODE_KEY_PREFIX + toEmail, authCode, 3, TimeUnit.MINUTES);

        Long newCount = redisTemplate.opsForValue().increment(hourlyKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(hourlyKey, HOURLY_LIMIT_WINDOW);
        }
    }

    // 2. 인증번호 검증 로직
    public boolean verifyCode(String email, String inputCode) {
        String redisCode = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + email);

        // 코드가 존재하고, 입력한 코드와 일치하면 true
        return redisCode != null && redisCode.equals(inputCode);
    }

    // 6자리 랜덤 숫자 생성
    private String createCode() {
        Random random = new Random();
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            key.append(random.nextInt(10));
        }
        return key.toString();
    }
}