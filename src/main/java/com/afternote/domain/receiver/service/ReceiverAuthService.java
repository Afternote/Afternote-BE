package com.afternote.domain.receiver.service;

import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.image.dto.PresignedUrlResponse;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.model.DeliveryVerification;
import com.afternote.domain.receiver.model.ReceivedRecordStatus;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.ReceivedRecordSort;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import com.afternote.domain.receiver.repository.AfternoteReceiverRepository;
import com.afternote.domain.receiver.repository.DeliveryVerificationRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final DeliveryVerificationRepository deliveryVerificationRepository;
    private final DeliveryConditionService deliveryConditionService;
    private final TimeLetterReceiverRepository timeLetterReceiverRepository;
    private final AfternoteReceiverRepository afternoteReceiverRepository;
    private final DiaryReceiverRepository diaryReceiverRepository;
    private final DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    private final UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;

    public ReceiverAuthVerifyResponse verifyAuthCode(String authCode) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        User sender = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_AUTH_CODE));
        return ReceiverAuthVerifyResponse.from(receiver, sender.getName());
    }

    public ReceivedTimeLetterListResponse getTimeLettersByAuthCode(String authCode) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        return receivedService.getTimeLetters(receiver.getId());
    }

    public ReceivedAfternoteListResponse getAfternotesByAuthCode(String authCode) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        return receivedService.getAfternotes(receiver.getId());
    }

    @Transactional
    public ReceivedTimeLetterResponse getTimeLetterByAuthCode(String authCode, Long timeLetterReceiverId) {
        Receiver receiver = findReceiverByAuthCode(authCode);
        return receivedService.getTimeLetter(receiver.getId(), timeLetterReceiverId);
    }

    public ReceivedAfternoteDetailResponse getAfternoteByAuthCode(String authCode, Long afternoteId) {
        Receiver receiver = findReceiverByAuthCode(authCode);
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
        return new ReceiverMessageResponse(sender.getName(), receiver.getMessage(), receiver.getCreatedAt());
    }

    public PresignedUrlResponse generatePresignedUrl(String authCode, String extension) {
        findReceiverByAuthCode(authCode);
        return s3Service.generatePresignedUrl("documents", extension, null);
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

    public void sendEmailAuthCode(String email) {
        String normalizedEmail = normalizeEmail(email);

        List<Receiver> receivers =
                receiverRepository.findAllByEmailIgnoreCaseOrderByIdDesc(normalizedEmail);

        if (receivers.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVER_EMAIL_NOT_FOUND);
        }

        Receiver receiver = receivers.get(0);

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

    public ReceivedRecordBoxListResponse getReceivedRecordBoxes(String authCode) {
        Receiver authenticatedReceiver = findReceiverByAuthCode(authCode);

        String email = normalizeEmail(authenticatedReceiver.getEmail());

        List<Receiver> receivers = receiverRepository.findAllByEmailIgnoreCaseOrderByIdDesc(email);

        if (receivers.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVER_NOT_FOUND);
        }

        Map<Long, User> senderMap = userRepository.findAllById(
                        receivers.stream()
                                .map(Receiver::getUserId)
                                .collect(Collectors.toSet())
                )
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ReceivedRecordBoxResponse> responses = receivers.stream()
                .map(receiver -> toReceivedRecordBoxResponse(receiver, senderMap))
                .toList();

        return ReceivedRecordBoxListResponse.from(responses);
    }

    public ReceivedRecordBoxResponse getReceivedRecordBox(String authCode, Long receiverId) {
        Receiver authenticatedReceiver = findReceiverByAuthCode(authCode);

        Receiver targetReceiver = receiverRepository.findById(receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

        if (!normalizeEmail(authenticatedReceiver.getEmail()).equals(normalizeEmail(targetReceiver.getEmail()))) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }

        User sender = userRepository.findById(targetReceiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        DeliveryVerification verification = deliveryVerificationRepository
                .findFirstByUserIdAndReceiverIdOrderByCreatedAtDesc(
                        sender.getId(),
                        targetReceiver.getId()
                )
                .orElse(null);

        ReceivedRecordStatus recordStatus = determineRecordStatus(targetReceiver.getId());
        boolean anyFulfilled = deliveryConditionService.hasAnyFulfilled(targetReceiver.getId());
        return ReceivedRecordBoxResponse.from(targetReceiver, sender, verification, recordStatus, anyFulfilled);    }

    private ReceivedRecordBoxResponse toReceivedRecordBoxResponse(
            Receiver receiver,
            Map<Long, User> senderMap
    ) {
        User sender = senderMap.get(receiver.getUserId());

        if (sender == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        DeliveryVerification verification = deliveryVerificationRepository
                .findFirstByUserIdAndReceiverIdOrderByCreatedAtDesc(
                        sender.getId(),
                        receiver.getId()
                )
                .orElse(null);

        ReceivedRecordStatus recordStatus = determineRecordStatus(receiver.getId());
        boolean anyFulfilled = deliveryConditionService.hasAnyFulfilled(receiver.getId());
        return ReceivedRecordBoxResponse.from(receiver, sender, verification, recordStatus, anyFulfilled);
    }

    private ReceivedRecordStatus determineRecordStatus(Long receiverId) {
        boolean hasTimeLetter = timeLetterReceiverRepository.existsByReceiverId(receiverId);
        boolean hasAfternote = afternoteReceiverRepository.existsByReceiverId(receiverId);
        boolean hasDiary = diaryReceiverRepository.existsByReceiverId(receiverId);
        boolean hasDeepThought = deepThoughtReceiverRepository.existsByReceiverId(receiverId);
        boolean hasDailyQuestion = userDailyQuestionReceiverRepository.existsByReceiverId(receiverId);

        if (hasTimeLetter || hasAfternote || hasDiary || hasDeepThought || hasDailyQuestion) {
            return ReceivedRecordStatus.STORED;
        }

        return ReceivedRecordStatus.EMPTY;
    }

    public ReceivedDiaryListResponse getReceivedDiaries(
            String authCode,
            ReceivedRecordSort sort,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Receiver receiver = findReceiverByAuthCode(authCode);

        return receivedService.getReceivedDiaries(
                receiver.getId(),
                sort,
                startDate,
                endDate
        );
    }

    public ReceivedDeepThoughtListResponse getReceivedDeepThoughts(
            String authCode,
            String category,
            String tag,
            ReceivedRecordSort sort,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Receiver receiver = findReceiverByAuthCode(authCode);

        return receivedService.getReceivedDeepThoughts(
                receiver.getId(),
                category,
                tag,
                sort,
                startDate,
                endDate
        );
    }

    public ReceivedDailyQuestionListResponse getReceivedDailyQuestions(
            String authCode,
            ReceivedRecordSort sort,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Receiver receiver = findReceiverByAuthCode(authCode);

        return receivedService.getReceivedDailyQuestions(
                receiver.getId(),
                sort,
                startDate,
                endDate
        );
    }
}
