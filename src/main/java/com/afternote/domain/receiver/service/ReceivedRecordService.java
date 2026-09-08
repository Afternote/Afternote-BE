package com.afternote.domain.receiver.service;

import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.image.dto.PresignedUrlResponse;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.receiver.repository.*;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivedRecordService {

    private final ReceiverRepository receiverRepository;
    private final UserRepository userRepository;
    private final ReceivedService receivedService;
    private final DeliveryVerificationService deliveryVerificationService;
    private final S3Service s3Service;
    private final DeliveryVerificationRepository deliveryVerificationRepository;
    private final DeliveryConditionService deliveryConditionService;
    private final TimeLetterReceiverRepository timeLetterReceiverRepository;
    private final AfternoteReceiverRepository afternoteReceiverRepository;
    private final DiaryReceiverRepository diaryReceiverRepository;
    private final DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    private final UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;

    public ReceivedRecordBoxListResponse getRecordBoxes(Long userId) {
        List<Receiver> receivers = receiverRepository.findAllByAcceptedUserIdOrderByIdDesc(userId);
        Map<Long, User> senderMap = userRepository.findAllById(
                        receivers.stream().map(Receiver::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return ReceivedRecordBoxListResponse.from(receivers.stream()
                .map(receiver -> toRecordBoxResponse(receiver, senderMap))
                .toList());
    }

    public ReceivedRecordBoxResponse getRecordBox(Long userId, Long receiverId) {
        Receiver receiver = findAcceptedReceiver(userId, receiverId);
        User sender = findSender(receiver);
        return toRecordBoxResponse(receiver, Map.of(sender.getId(), sender));
    }

    public ReceivedTimeLetterListResponse getTimeLetters(Long userId, Long receiverId) {
        return receivedService.getTimeLetters(findAcceptedReceiver(userId, receiverId).getId());
    }

    @Transactional
    public ReceivedTimeLetterResponse getTimeLetter(Long userId, Long receiverId, Long timeLetterReceiverId) {
        return receivedService.getTimeLetter(
                findAcceptedReceiver(userId, receiverId).getId(), timeLetterReceiverId);
    }

    public ReceivedAfternoteListResponse getAfternotes(Long userId, Long receiverId) {
        return receivedService.getAfternotes(findAcceptedReceiver(userId, receiverId).getId());
    }

    public ReceivedAfternoteDetailResponse getAfternote(Long userId, Long receiverId, Long afternoteId) {
        return receivedService.getAfternote(
                findAcceptedReceiver(userId, receiverId).getId(), afternoteId);
    }

    public ReceivedDiaryListResponse getDiaries(
            Long userId, Long receiverId, ReceivedRecordSort sort, LocalDate startDate, LocalDate endDate
    ) {
        return receivedService.getReceivedDiaries(
                findAcceptedReceiver(userId, receiverId).getId(), sort, startDate, endDate);
    }

    public ReceivedDeepThoughtListResponse getDeepThoughts(
            Long userId, Long receiverId, String category, String tag,
            ReceivedRecordSort sort, LocalDate startDate, LocalDate endDate
    ) {
        return receivedService.getReceivedDeepThoughts(
                findAcceptedReceiver(userId, receiverId).getId(), category, tag, sort, startDate, endDate);
    }

    public ReceivedDailyQuestionListResponse getDailyQuestions(
            Long userId, Long receiverId, ReceivedRecordSort sort, LocalDate startDate, LocalDate endDate
    ) {
        return receivedService.getReceivedDailyQuestions(
                findAcceptedReceiver(userId, receiverId).getId(), sort, startDate, endDate);
    }

    public ReceiverMessageResponse getMessage(Long userId, Long receiverId) {
        Receiver receiver = findAcceptedReceiver(userId, receiverId);
        User sender = findSender(receiver);
        return new ReceiverMessageResponse(sender.getName(), receiver.getMessage(), receiver.getCreatedAt());
    }

    public PresignedUrlResponse generatePresignedUrl(
            Long userId, Long receiverId, String extension, Long contentLength
    ) {
        findAcceptedReceiver(userId, receiverId);
        return s3Service.generatePresignedUrl("documents", extension, contentLength, null);
    }

    @Transactional
    public DeliveryVerificationResponse submitDeliveryVerification(
            Long userId, Long receiverId, DeliveryVerificationRequest request
    ) {
        Receiver receiver = findAcceptedReceiver(userId, receiverId);
        return DeliveryVerificationResponse.from(
                deliveryVerificationService.submitVerification(
                        receiver,
                        request.getDeathCertificateUrl(),
                        request.getFamilyRelationCertificateUrl()),
                s3Service::resolvePublicUrl);
    }

    public DeliveryVerificationResponse getDeliveryVerificationStatus(Long userId, Long receiverId) {
        Receiver receiver = findAcceptedReceiver(userId, receiverId);
        return DeliveryVerificationResponse.from(
                deliveryVerificationService.getVerificationStatus(receiver),
                s3Service::resolvePublicUrl);
    }

    private Receiver findAcceptedReceiver(Long userId, Long receiverId) {
        return receiverRepository.findByIdAndAcceptedUserId(receiverId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));
    }

    private User findSender(Receiver receiver) {
        return userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private ReceivedRecordBoxResponse toRecordBoxResponse(Receiver receiver, Map<Long, User> senderMap) {
        User sender = senderMap.get(receiver.getUserId());
        if (sender == null) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        DeliveryVerification verification = deliveryVerificationRepository
                .findFirstByUserIdAndReceiverIdOrderByCreatedAtDesc(sender.getId(), receiver.getId())
                .orElse(null);
        boolean anyFulfilled = deliveryConditionService.hasAnyFulfilled(receiver.getId());
        return ReceivedRecordBoxResponse.from(
                receiver, sender, verification, determineRecordStatus(receiver.getId()), anyFulfilled);
    }

    private ReceivedRecordStatus determineRecordStatus(Long receiverId) {
        if (timeLetterReceiverRepository.existsByReceiverId(receiverId)
                || afternoteReceiverRepository.existsByReceiverId(receiverId)
                || diaryReceiverRepository.existsByReceiverId(receiverId)
                || deepThoughtReceiverRepository.existsByReceiverId(receiverId)
                || userDailyQuestionReceiverRepository.existsByReceiverId(receiverId)) {
            return ReceivedRecordStatus.STORED;
        }
        return ReceivedRecordStatus.EMPTY;
    }
}
