package com.afternote.domain.receiver.service;

import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteReceiver;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.receiver.repository.AfternoteReceiverRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceivedService {

    private final ReceiverRepository receiverRepository;
    private final TimeLetterReceiverRepository timeLetterReceiverRepository;
    private final AfternoteReceiverRepository afternoteReceiverRepository;
    private final TimeLetterRepository timeLetterRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;

    /**
     * 수신자가 받은 타임레터 목록 조회
     * - 수신자 ID 기준으로 TimeLetterReceiver 목록을 조회한다.
     * - TimeLetter 내부의 blocks를 기준으로 본문을 응답한다.
     */
    public ReceivedTimeLetterListResponse getTimeLetters(Long receiverId) {
        validateReceiver(receiverId);

        List<TimeLetterReceiver> timeLetterReceivers =
                timeLetterReceiverRepository.findByReceiverIdWithTimeLetter(receiverId);

        List<ReceivedTimeLetterResponse> responses = timeLetterReceivers.stream()
                .map(tlr -> ReceivedTimeLetterResponse.from(
                        tlr,
                        s3Service::generateGetPresignedUrl
                ))
                .toList();

        return ReceivedTimeLetterListResponse.from(responses);
    }

    /**
     * 수신한 타임레터 상세 조회
     * - 수신자 본인에게 연결된 타임레터인지 검증한다.
     * - sendAt이 지난 경우에만 읽음 처리한다.
     * - TimeLetter의 blocks를 기준으로 상세 본문을 응답한다.
     */
    @Transactional
    public ReceivedTimeLetterResponse getTimeLetter(Long receiverId, Long timeLetterReceiverId) {
        validateReceiver(receiverId);

        TimeLetterReceiver timeLetterReceiver = timeLetterReceiverRepository
                .findByIdAndReceiverIdWithTimeLetter(timeLetterReceiverId, receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND));

        // 발송 예정 시간이 지났을 때만 읽음 처리
        if (timeLetterReceiver.getTimeLetter().getSendAt() != null
                && !timeLetterReceiver.getTimeLetter().getSendAt().isAfter(LocalDateTime.now())) {
            timeLetterReceiver.markAsRead();
        }

        return ReceivedTimeLetterResponse.from(
                timeLetterReceiver,
                s3Service::generateGetPresignedUrl
        );
    }

    /**
     * 수신자가 받은 애프터노트 목록 조회
     */
    public ReceivedAfternoteListResponse getAfternotes(Long receiverId) {
        validateReceiver(receiverId);

        List<AfternoteReceiver> afternoteReceivers =
                afternoteReceiverRepository.findByReceiverIdWithAfternote(receiverId);

        // Afternote은 User 객체를 가지고 있으므로 User 정보를 직접 조회
        Set<Long> userIds = afternoteReceivers.stream()
                .map(ar -> ar.getAfternote().getUser().getId())
                .collect(Collectors.toSet());

        var userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ReceivedAfternoteResponse> responses = afternoteReceivers.stream()
                .map(ar -> {
                    User sender = userMap.get(ar.getAfternote().getUser().getId());
                    String senderName = sender != null ? sender.getName() : "알 수 없음";
                    return ReceivedAfternoteResponse.from(ar, senderName);
                })
                .toList();

        return ReceivedAfternoteListResponse.from(responses);
    }

    /**
     * 수신한 애프터노트 상세 조회
     */
    public ReceivedAfternoteDetailResponse getAfternote(Long receiverId, Long afternoteId) {
        AfternoteReceiver afternoteReceiver = afternoteReceiverRepository
                .findByAfternoteIdAndReceiverIdWithAfternote(afternoteId, receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.AFTERNOTE_NOT_FOUND));

        Afternote afternote = afternoteReceiver.getAfternote();

        User sender = userRepository.findById(afternote.getUser().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String senderName = sender.getName();

        return switch (afternote.getCategoryType()) {
            case SOCIAL -> ReceivedAfternoteDetailResponse.fromSocial(afternote, senderName);
            case GALLERY -> ReceivedAfternoteDetailResponse.fromGallery(afternote, senderName);
            case PLAYLIST -> ReceivedAfternoteDetailResponse.fromPlaylist(
                    afternote,
                    senderName,
                    s3Service::resolvePublicUrl
            );
        };
    }

    /**
     * 타임레터에 수신자 등록
     * - 별도 수신자 등록 API에서 사용한다.
     * - timeLetterId 기준으로 타임레터 소유권을 검증한다.
     * - receiverIds의 존재 여부와 소유권을 검증한 뒤 TimeLetterReceiver를 생성한다.
     */
    @Transactional
    public List<Long> createTimeLetterReceivers(Long userId, CreateTimeLetterReceiverRequest request) {
        TimeLetter timeLetter = timeLetterRepository.findByIdAndUserId(request.getTimeLetterID(), userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND));

        List<Long> uniqueReceiverIds = normalizeReceiverIds(request.getReceiverIds());

        List<Receiver> receivers = receiverRepository.findAllById(uniqueReceiverIds);
        if (receivers.size() != uniqueReceiverIds.size()) {
            throw new CustomException(ErrorCode.RECEIVER_NOT_FOUND);
        }

        // 본인이 등록한 수신자인지 검증
        validateReceiversOwnership(userId, receivers);

        // deliveredAt이 없으면 타임레터의 sendAt으로 대체
        LocalDateTime effectiveDeliveredAt = request.getDeliveredAt() != null
                ? request.getDeliveredAt()
                : timeLetter.getSendAt();

        if (effectiveDeliveredAt == null) {
            throw new CustomException(ErrorCode.TIME_LETTER_REQUIRED_FIELDS);
        }

        List<TimeLetterReceiver> timeLetterReceivers = receivers.stream()
                .map(receiver -> TimeLetterReceiver.builder()
                        .timeLetter(timeLetter)
                        .receiver(receiver)
                        .deliveredAt(effectiveDeliveredAt)
                        .build())
                .toList();

        return timeLetterReceiverRepository.saveAll(timeLetterReceivers).stream()
                .map(TimeLetterReceiver::getId)
                .toList();
    }

    /**
     * 타임레터 생성 시 수신자 등록
     * - TimeLetterService에서 타임레터 생성과 함께 호출한다.
     * - 전제조건: 호출자가 TimeLetter의 소유권을 이미 검증했거나, 방금 생성한 TimeLetter를 전달해야 한다.
     */
    @Transactional
    public List<Long> createTimeLetterReceivers(
            TimeLetter timeLetter,
            Long userId,
            List<Long> receiverIds,
            LocalDateTime deliveredAt
    ) {
        List<Long> uniqueReceiverIds = normalizeReceiverIds(receiverIds);

        List<Receiver> receivers = receiverRepository.findAllById(uniqueReceiverIds);
        if (receivers.size() != uniqueReceiverIds.size()) {
            throw new CustomException(ErrorCode.RECEIVER_NOT_FOUND);
        }

        // 본인이 등록한 수신자인지 검증
        validateReceiversOwnership(userId, receivers);

        // deliveredAt이 null이면 timeLetter의 sendAt으로 대체
        LocalDateTime effectiveDeliveredAt = deliveredAt != null
                ? deliveredAt
                : timeLetter.getSendAt();

        if (effectiveDeliveredAt == null) {
            throw new CustomException(ErrorCode.TIME_LETTER_REQUIRED_FIELDS);
        }

        List<TimeLetterReceiver> timeLetterReceivers = receivers.stream()
                .map(receiver -> TimeLetterReceiver.builder()
                        .timeLetter(timeLetter)
                        .receiver(receiver)
                        .deliveredAt(effectiveDeliveredAt)
                        .build())
                .toList();

        return timeLetterReceiverRepository.saveAll(timeLetterReceivers).stream()
                .map(TimeLetterReceiver::getId)
                .toList();
    }

    /**
     * 수신자 ID 목록 정규화
     * - null 또는 빈 목록이면 예외를 발생시킨다.
     * - null 원소를 제거한다.
     * - 중복된 수신자 ID를 제거한다.
     */
    private List<Long> normalizeReceiverIds(List<Long> receiverIds) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }

        List<Long> uniqueReceiverIds = new ArrayList<>(new LinkedHashSet<>(
                receiverIds.stream()
                        .filter(Objects::nonNull)
                        .toList()
        ));

        if (uniqueReceiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }

        return uniqueReceiverIds;
    }

    /**
     * 수신자가 본인이 등록한 수신자인지 검증
     */
    private void validateReceiversOwnership(Long userId, List<Receiver> receivers) {
        boolean hasUnauthorizedReceiver = receivers.stream()
                .anyMatch(receiver -> !receiver.getUserId().equals(userId));

        if (hasUnauthorizedReceiver) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }
    }

    /**
     * 수신자 존재 여부 및 배달 조건 검증
     * - receiverId가 존재하는지 확인한다.
     * - 해당 수신자를 등록한 발신자의 배달 조건이 충족되었는지 확인한다.
     */
    private void validateReceiver(Long receiverId) {
        Receiver receiver = receiverRepository.findById(receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

        User sender = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!sender.isDeliveryConditionMet()) {
            throw new CustomException(ErrorCode.DELIVERY_CONDITION_NOT_MET);
        }
    }
}