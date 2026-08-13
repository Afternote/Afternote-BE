package com.afternote.domain.auth.service;

import com.afternote.domain.auth.event.EmailVerificationMailRunner;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String CODE_KEY_PREFIX = "EMAIL:";
    private static final String VERIFIED_KEY_PREFIX = "EMAIL_VERIFIED:";
    private static final String COOLDOWN_KEY_PREFIX = "EMAIL_COOLDOWN:";
    private static final String HOURLY_COUNT_KEY_PREFIX = "EMAIL_SEND_COUNT:";
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(10);
    private static final Duration HOURLY_LIMIT_WINDOW = Duration.ofHours(1);
    private static final Duration CODE_TTL = Duration.ofMinutes(3);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);
    private static final long MAX_SENDS_PER_HOUR = 5L;

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailVerificationMailRunner emailVerificationMailRunner;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * 인증번호를 Redis에 저장한 뒤 SMTP는 비동기로 발송한다.
     * @return 인증번호 만료 시각 (UTC)
     */
    public Instant sendCode(String toEmail, EmailVerificationPurpose purpose) {
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
        Instant expiresAt = Instant.now().plus(CODE_TTL);

        redisTemplate.opsForValue().set(codeKey, authCode, CODE_TTL);

        Long newCount = redisTemplate.opsForValue().increment(hourlyKey);
        if (newCount != null && newCount == 1L) {
            redisTemplate.expire(hourlyKey, HOURLY_LIMIT_WINDOW);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("[AfterNote] 이메일 인증 번호입니다.");
        message.setText("인증 번호는 [" + authCode + "] 입니다. "
                + CODE_TTL.toMinutes() + "분 안에 입력해주세요.");
        message.setFrom(senderEmail);

        emailVerificationMailRunner.send(message, codeKey, cooldownKey);

        return expiresAt;
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

    public void markVerified(String email, EmailVerificationPurpose purpose) {
        redisTemplate.opsForValue().set(buildVerifiedKey(email, purpose), "1", VERIFIED_TTL);
    }

    /**
     * 회원가입 등에서 이메일 인증 완료 여부를 소비한다.
     * @return 인증 완료 상태가 있으면 true (플래그 삭제), 없으면 false
     */
    public boolean consumeVerified(String email, EmailVerificationPurpose purpose) {
        String verifiedKey = buildVerifiedKey(email, purpose);
        String value = redisTemplate.opsForValue().get(verifiedKey);
        if (value == null) {
            return false;
        }
        redisTemplate.delete(verifiedKey);
        return true;
    }

    private String buildCodeKey(String email, EmailVerificationPurpose purpose) {
        return CODE_KEY_PREFIX + purpose.name() + ":" + email;
    }

    private String buildVerifiedKey(String email, EmailVerificationPurpose purpose) {
        return VERIFIED_KEY_PREFIX + purpose.name() + ":" + email;
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
