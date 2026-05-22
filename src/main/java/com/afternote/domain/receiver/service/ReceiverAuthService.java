package com.afternote.domain.receiver.service;

import com.afternote.domain.image.dto.PresignedUrlResponse;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiverAuthService {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Duration EMAIL_AUTH_CODE_TTL = Duration.ofMinutes(5);
    private static final String EMAIL_AUTH_CODE_PREFIX = "receiver:email-auth:";

    private final ReceiverRepository receiverRepository;
    private final UserRepository userRepository;
    private final ReceivedService receivedService;
    private final DeliveryVerificationService deliveryVerificationService;
    private final S3Service s3Service;
    private final AuthCodeMessageService authCodeMessageService;
    private final StringRedisTemplate stringRedisTemplate;

    public ReceiverAuthVerifyResponse verifyAuthCode(String authCode) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        User sender = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_AUTH_CODE));
        return ReceiverAuthVerifyResponse.from(receiver, sender.getName());
    }

    public ReceivedTimeLetterListResponse getTimeLettersByAuthCode(String authCode) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        validateDeliveryCondition(receiver);
        return receivedService.getTimeLetters(receiver.getId());
    }

    public ReceivedAfternoteListResponse getAfternotesByAuthCode(String authCode) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        validateDeliveryCondition(receiver);
        return receivedService.getAfternotes(receiver.getId());
    }

    @Transactional
    public ReceivedTimeLetterResponse getTimeLetterByAuthCode(String authCode, Long timeLetterReceiverId) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        validateDeliveryCondition(receiver);
        return receivedService.getTimeLetter(receiver.getId(), timeLetterReceiverId);
    }

    public ReceivedAfternoteDetailResponse getAfternoteByAuthCode(String authCode, Long afternoteId) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        validateDeliveryCondition(receiver);
        return receivedService.getAfternote(receiver.getId(), afternoteId);
    }

    @Transactional
    public DeliveryVerificationResponse submitDeliveryVerification(String authCode, DeliveryVerificationRequest request) {
        findReceiverByAuthCode(authCode);
        return DeliveryVerificationResponse.from(
                deliveryVerificationService.submitVerification(
                        authCode,
                        request.getDeathCertificateUrl(),
                        request.getFamilyRelationCertificateUrl()
            ),
            s3Service::resolvePublicUrl
        );
    }

    public ReceiverMessageResponse getMessageByAuthCode(String authCode) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        User sender = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return new ReceiverMessageResponse(sender.getName(), receiver.getMessage());
    }

    public PresignedUrlResponse generatePresignedUrl(String authCode, String extension) {
        findReceiverByAuthCode(authCode);
        return s3Service.generatePresignedUrl("documents", extension);
    }

    public DeliveryVerificationResponse getDeliveryVerificationStatus(String authCode) {
        findReceiverByAuthCode(authCode);
        return DeliveryVerificationResponse.from(
                deliveryVerificationService.getVerificationStatus(authCode),
                s3Service::resolvePublicUrl
        );
    }

    private Receiver findReceiverByAuthCode(String authCode) {
        if (authCode == null || !UUID_PATTERN.matcher(authCode.toLowerCase()).matches()) {
            throw new CustomException(ErrorCode.INVALID_AUTH_CODE);
        }
        return receiverRepository.findByAuthCode(authCode.toLowerCase())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_AUTH_CODE));
    }

    private void validateDeliveryCondition(Receiver receiver) {
        User sender = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!sender.isDeliveryConditionMet()) {
            throw new CustomException(ErrorCode.DELIVERY_CONDITION_NOT_MET);
        }
    }

    public void sendEmailAuthCode(String email) {
        String normalizedEmail = normalizeEmail(email);

        Receiver receiver = receiverRepository.findFirstByEmailIgnoreCaseOrderByIdDesc(normalizedEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_EMAIL_NOT_FOUND));

        User sender = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String emailAuthCode = generateSixDigitCode();

        String redisKey = EMAIL_AUTH_CODE_PREFIX + normalizedEmail;
        String redisValue = receiver.getId() + ":" + emailAuthCode;

        try {
            stringRedisTemplate.opsForValue().set(
                    redisKey,
                    redisValue,
                    EMAIL_AUTH_CODE_TTL
            );

            authCodeMessageService.sendAuthCode(
                    receiver.getEmail(),
                    emailAuthCode,
                    sender.getName(),
                    receiver.getName()
            );
        } catch (Exception e) {
            stringRedisTemplate.delete(redisKey);
            throw new CustomException(ErrorCode.RECEIVER_EMAIL_SEND_FAILED);
        }
    }

    public ReceiverEmailAuthVerifyResponse verifyEmailAuthCode(String email, String inputAuthCode) {
        String normalizedEmail = normalizeEmail(email);
        String redisKey = EMAIL_AUTH_CODE_PREFIX + normalizedEmail;

        String savedValue = stringRedisTemplate.opsForValue().get(redisKey);

        if (savedValue == null) {
            throw new CustomException(ErrorCode.RECEIVER_EMAIL_AUTH_CODE_NOT_FOUND);
        }

        String[] values = savedValue.split(":");

        if (values.length != 2) {
            stringRedisTemplate.delete(redisKey);
            throw new CustomException(ErrorCode.RECEIVER_EMAIL_AUTH_CODE_NOT_FOUND);
        }

        Long receiverId = Long.parseLong(values[0]);
        String savedAuthCode = values[1];

        if (!savedAuthCode.equals(inputAuthCode)) {
            throw new CustomException(ErrorCode.RECEIVER_EMAIL_AUTH_CODE_MISMATCH);
        }

        Receiver receiver = receiverRepository.findById(receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

        User sender = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        stringRedisTemplate.delete(redisKey);

        return ReceiverEmailAuthVerifyResponse.from(receiver, sender.getName());
    }

    private String generateSixDigitCode() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 1000000));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
