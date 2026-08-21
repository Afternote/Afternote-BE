package com.afternote.domain.timeletter.service;

import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.receiver.service.ReceivedService;
import com.afternote.domain.timeletter.dto.request.TimeLetterBlockRequest;
import com.afternote.domain.timeletter.dto.request.TimeLetterCreateRequest;
import com.afternote.domain.timeletter.dto.request.TimeLetterDeleteRequest;
import com.afternote.domain.timeletter.dto.request.TimeLetterUpdateRequest;
import com.afternote.domain.timeletter.dto.response.TimeLetterListResponse;
import com.afternote.domain.timeletter.dto.response.TimeLetterResponse;
import com.afternote.domain.timeletter.model.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.event.UserActivityTouchedEvent;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeLetterService {

    private final TimeLetterRepository timeLetterRepository;
    private final TimeLetterReceiverRepository timeLetterReceiverRepository;
    private final UserRepository userRepository;
    private final ReceivedService receivedService;
    private final S3Service s3Service;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 정식 등록된 타임레터 전체 조회
     * - SCHEDULED 상태의 타임레터만 조회한다.
     * - 수신자 ID 목록은 별도로 일괄 조회하여 N+1 문제를 방지한다.
     */
    @Transactional(readOnly = true)
    public TimeLetterListResponse getTimeLetters(Long userId) {
        List<TimeLetter> timeLetters = timeLetterRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, TimeLetterStatus.SCHEDULED);

        Map<Long, List<Long>> receiverIdsMap = fetchReceiverIdsMap(timeLetters);

        List<TimeLetterResponse> responses = timeLetters.stream()
                .map(timeLetter -> TimeLetterResponse.from(
                        timeLetter,
                        s3Service::generateGetPresignedUrl,
                        receiverIdsMap.getOrDefault(timeLetter.getId(), List.of())
                ))
                .collect(Collectors.toList());

        return TimeLetterListResponse.from(responses);
    }

    /**
     * 타임레터 단일 조회
     * - 요청한 사용자의 타임레터인지 소유권을 검증한다.
     * - 본문 블록과 수신자 ID 목록을 함께 응답한다.
     */
    @Transactional(readOnly = true)
    public TimeLetterResponse getTimeLetter(Long userId, Long timeLetterId) {
        TimeLetter timeLetter = findTimeLetterWithOwnership(userId, timeLetterId);

        List<Long> receiverIds = timeLetterReceiverRepository.findByTimeLetterId(timeLetterId).stream()
                .map(tlr -> tlr.getReceiver().getId())
                .collect(Collectors.toList());

        return TimeLetterResponse.from(
                timeLetter,
                s3Service::generateGetPresignedUrl,
                receiverIds
        );
    }

    /**
     * 타임레터 생성
     * - 임시저장(DRAFT), 정식등록(SCHEDULED) 모두 생성 가능하다.
     * - 정책상 임시저장/정식등록 모두 수신자가 반드시 필요하다.
     * - 타임레터 저장, 본문 블록 저장, 수신자 등록을 하나의 트랜잭션으로 처리한다.
     */
    @Transactional
    public TimeLetterResponse createTimeLetter(Long userId, TimeLetterCreateRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        // 프록시만 사용해 users 행 dirty/공유 락 면적을 줄인다 (동시 create)
        User user = userRepository.getReferenceById(userId);

        // 콘텐츠 작성은 활동으로 간주 — last_active_at 은 커밋 후 벌크 갱신
        eventPublisher.publishEvent(new UserActivityTouchedEvent(userId));

        TimeLetterDeliveryMode deliveryMode = request.getDeliveryMode() != null
                ? request.getDeliveryMode()
                : TimeLetterDeliveryMode.DATE;

        // 수신자 ID는 DRAFT/SCHEDULED 모두 필수
        List<Long> receiverIds = normalizeReceiverIds(request.getReceiverIds());

        // 본문 블록 형식 검증
        validateBlocks(request.getBlocks());

        // 정식 등록 상태라면 제목, 본문, (DATE 모드는) 발송 예정 시간을 필수로 검증
        if (request.getStatus() == TimeLetterStatus.SCHEDULED) {
            validateForScheduled(
                    request.getTitle(),
                    request.getSendAt(),
                    hasContentBlock(request.getBlocks()),
                    deliveryMode
            );
        }

        // 타임레터 기본 정보 생성
        TimeLetter timeLetter = TimeLetter.builder()
                .user(user)
                .title(request.getTitle())
                .sendAt(deliveryMode == TimeLetterDeliveryMode.POST_DEATH ? null : request.getSendAt())
                .status(request.getStatus())
                .deliveryMode(deliveryMode)
                .build();

        // 요청으로 받은 blocks를 TimeLetterBlock 엔티티로 변환 후 연결
        List<TimeLetterBlock> blocks = buildBlocks(userId, request.getBlocks());
        timeLetter.replaceBlocks(blocks);

        // TimeLetter 저장
        TimeLetter savedTimeLetter = timeLetterRepository.save(timeLetter);

        // 수신자 등록
        // ReceivedService 내부에서 수신자 존재 여부와 소유권 검증을 수행한다.
        receivedService.createTimeLetterReceivers(
                savedTimeLetter,
                userId,
                receiverIds,
                request.getSendAt()
        );

        return TimeLetterResponse.from(
                savedTimeLetter,
                s3Service::generateGetPresignedUrl,
                receiverIds
        );
    }

    /**
     * 임시저장된 타임레터 전체 조회
     * - DRAFT 상태의 타임레터만 조회한다.
     * - 수신자 ID 목록은 별도로 일괄 조회하여 N+1 문제를 방지한다.
     */
    @Transactional(readOnly = true)
    public TimeLetterListResponse getTemporaryTimeLetters(Long userId) {
        List<TimeLetter> timeLetters = timeLetterRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, TimeLetterStatus.DRAFT);

        Map<Long, List<Long>> receiverIdsMap = fetchReceiverIdsMap(timeLetters);

        List<TimeLetterResponse> responses = timeLetters.stream()
                .map(timeLetter -> TimeLetterResponse.from(
                        timeLetter,
                        s3Service::generateGetPresignedUrl,
                        receiverIdsMap.getOrDefault(timeLetter.getId(), List.of())
                ))
                .collect(Collectors.toList());

        return TimeLetterListResponse.from(responses);
    }

    /**
     * 타임레터 삭제
     * - 단일/다건 삭제를 모두 지원한다.
     * - 요청한 ID 목록 중 소유하지 않은 타임레터가 있으면 예외를 발생시킨다.
     * - 이미 발송된 SENT 상태의 타임레터는 삭제할 수 없다.
     * - 타임레터 삭제 전 수신자 연결 정보를 먼저 삭제한다.
     */
    @Transactional
    public void deleteTimeLetters(Long userId, TimeLetterDeleteRequest request) {
        List<TimeLetter> timeLetters = timeLetterRepository
                .findByIdInAndUserId(request.getTimeLetterIds(), userId);

        // 동시 삭제: 이미 지워진 경우 500 대신 1006
        if (timeLetters.isEmpty()) {
            throw new CustomException(ErrorCode.RESOURCE_ALREADY_DELETED);
        }
        // 일부만 없으면 잘못된 ID 포함(기존 계약) — 전부 못 찾은 경우와 구분
        if (timeLetters.size() != request.getTimeLetterIds().size()) {
            throw new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND);
        }

        // SENT 상태 검증
        for (TimeLetter timeLetter : timeLetters) {
            if (!timeLetter.isModifiable()) {
                throw new CustomException(ErrorCode.TIME_LETTER_ALREADY_SENT);
            }
        }

        // FK 제약 때문에 TimeLetter를 삭제하기 전에 수신자 연결 정보를 먼저 삭제해야 한다.
        timeLetterReceiverRepository.deleteByTimeLetterIdIn(request.getTimeLetterIds());

        // TimeLetter 삭제 시 blocks는 cascade + orphanRemoval로 함께 삭제된다.
        timeLetterRepository.deleteAll(timeLetters);
        // 동시 삭제 stale를 요청 스레드에서 즉시 표면화 → GlobalExceptionHandler 1006
        timeLetterRepository.flush();
    }
    /**
     * 임시저장된 타임레터 전체 삭제
     * - 현재 사용자의 DRAFT 상태 타임레터를 모두 삭제한다.
     * - 타임레터 삭제 전 수신자 연결 정보를 먼저 삭제한다.
     */
    @Transactional
    public void deleteAllTemporary(Long userId) {
        List<TimeLetter> draftTimeLetters = timeLetterRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(userId, TimeLetterStatus.DRAFT);

        if (!draftTimeLetters.isEmpty()) {
            List<Long> draftIds = draftTimeLetters.stream()
                    .map(TimeLetter::getId)
                    .toList();

            // FK 제약 때문에 TimeLetter를 삭제하기 전에 수신자 연결 정보를 먼저 삭제해야 한다.
            timeLetterReceiverRepository.deleteByTimeLetterIdIn(draftIds);

            // TimeLetter 삭제 시 blocks는 cascade + orphanRemoval로 함께 삭제된다.
            timeLetterRepository.deleteAll(draftTimeLetters);
        }
    }

    /**
     * 타임레터 수정
     * - 제목, 발송 예정 시간, 상태, 본문 블록을 수정할 수 있다.
     * - 이미 발송된 SENT 상태의 타임레터는 수정할 수 없다.
     * - SCHEDULED 상태로 변경하는 경우 필수값과 수신자 등록 여부를 검증한다.
     */
    @Transactional
    public TimeLetterResponse updateTimeLetter(Long userId, Long timeLetterId, TimeLetterUpdateRequest request) {
        // 동일 ID 동시 PATCH: 행 락으로 blocks 교체를 직렬화 (둘 다 200, 나중 요청이 최종 반영)
        TimeLetter timeLetter = timeLetterRepository.findByIdAndUserIdForUpdate(timeLetterId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND));

        // SENT 상태면 수정 불가
        if (!timeLetter.isModifiable()) {
            throw new CustomException(ErrorCode.TIME_LETTER_ALREADY_SENT);
        }

        // 요청에 값이 없으면 기존 값을 유지한다.
        TimeLetterStatus newStatus = request.getStatus() != null
                ? request.getStatus()
                : timeLetter.getStatus();

        String newTitle = request.getTitle() != null
                ? request.getTitle()
                : timeLetter.getTitle();

        TimeLetterDeliveryMode newDeliveryMode = request.getDeliveryMode() != null
                ? request.getDeliveryMode()
                : timeLetter.getDeliveryMode();

        // POST_DEATH 모드는 발송일을 사용하지 않으므로 sendAt을 비운다.
        LocalDateTime newSendAt = newDeliveryMode == TimeLetterDeliveryMode.POST_DEATH
                ? null
                : (request.getSendAt() != null ? request.getSendAt() : timeLetter.getSendAt());

        // blocks가 요청에 포함되면 요청 blocks 기준으로, 없으면 기존 blocks 기준으로 본문 존재 여부 확인
        boolean hasContent = request.getBlocks() != null
                ? hasContentBlock(request.getBlocks())
                : hasContentBlockFromEntity(timeLetter.getBlocks());

        // 정식 등록 상태라면 필수값과 수신자 등록 여부를 검증
        if (newStatus == TimeLetterStatus.SCHEDULED) {
            validateForScheduled(newTitle, newSendAt, hasContent, newDeliveryMode);
            validateReceiversRegistered(timeLetterId);
        }

        // 타임레터 기본 정보 수정
        timeLetter.update(newTitle, newSendAt, newStatus, newDeliveryMode);

        // blocks가 요청에 포함된 경우에만 기존 블록을 새 블록으로 교체한다.
        if (request.getBlocks() != null) {
            validateBlocks(request.getBlocks());

            List<TimeLetterBlock> newBlocks = buildBlocks(userId, request.getBlocks());
            timeLetter.replaceBlocks(newBlocks);
        }

        List<Long> receiverIds = timeLetterReceiverRepository.findByTimeLetterId(timeLetterId).stream()
                .map(tlr -> tlr.getReceiver().getId())
                .collect(Collectors.toList());

        return TimeLetterResponse.from(
                timeLetter,
                s3Service::generateGetPresignedUrl,
                receiverIds
        );
    }

    /**
     * 타임레터 목록에 대한 수신자 ID 일괄 조회
     * - 목록 조회 시 각 타임레터마다 수신자 조회 쿼리가 발생하는 N+1 문제를 방지한다.
     */
    private Map<Long, List<Long>> fetchReceiverIdsMap(List<TimeLetter> timeLetters) {
        if (timeLetters.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> timeLetterIds = timeLetters.stream()
                .map(TimeLetter::getId)
                .toList();

        return timeLetterReceiverRepository.findByTimeLetterIdIn(timeLetterIds).stream()
                .collect(Collectors.groupingBy(
                        tlr -> tlr.getTimeLetter().getId(),
                        Collectors.mapping(tlr -> tlr.getReceiver().getId(), Collectors.toList())
                ));
    }

    /**
     * 소유권 검증 후 타임레터 조회
     * - timeLetterId와 userId가 모두 일치하는 타임레터만 조회한다.
     */
    private TimeLetter findTimeLetterWithOwnership(Long userId, Long timeLetterId) {
        return timeLetterRepository.findByIdAndUserId(timeLetterId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND));
    }

    /**
     * SCHEDULED 상태 필수값 검증
     * - 정식 등록 상태에서는 제목, 본문, 발송 예정 시간이 모두 필요하다.
     * - 발송 예정 시간은 현재 시간 이후여야 한다.
     */
    private void validateForScheduled(String title, LocalDateTime sendAt, boolean hasContent,
                                      TimeLetterDeliveryMode deliveryMode) {
        // POST_DEATH 모드는 발송일이 없으므로 제목/본문만 검증한다.
        if (deliveryMode == TimeLetterDeliveryMode.POST_DEATH) {
            if (title == null || title.isBlank() || !hasContent) {
                throw new CustomException(ErrorCode.TIME_LETTER_REQUIRED_FIELDS);
            }
            return;
        }

        if (title == null || title.isBlank() || sendAt == null || !hasContent) {
            throw new CustomException(ErrorCode.TIME_LETTER_REQUIRED_FIELDS);
        }

        if (sendAt.isBefore(LocalDateTime.now())) {
            throw new CustomException(ErrorCode.TIME_LETTER_INVALID_SEND_DATE);
        }
    }

    /**
     * 수신자 ID 목록 정규화 및 검증
     * - null 또는 빈 목록이면 예외를 발생시킨다.
     * - null 원소를 제거하고 중복 ID를 제거한다.
     * - 중복 제거 후에도 비어 있으면 예외를 발생시킨다.
     */
    private List<Long> normalizeReceiverIds(List<Long> receiverIds) {
        if (receiverIds == null || receiverIds.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }

        List<Long> normalizedIds = receiverIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (normalizedIds.isEmpty()) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }

        return normalizedIds;
    }

    /**
     * 타임레터에 수신자가 등록되어 있는지 검증
     * - DRAFT에서 SCHEDULED로 변경할 때 사용한다.
     */
    private void validateReceiversRegistered(Long timeLetterId) {
        boolean hasReceiver = timeLetterReceiverRepository.existsByTimeLetterId(timeLetterId);

        if (!hasReceiver) {
            throw new CustomException(ErrorCode.RECEIVERS_REQUIRED);
        }
    }

    /**
     * 요청 DTO를 TimeLetterBlock 엔티티 목록으로 변환
     * - TEXT 블록은 textContent를 사용한다.
     * - IMAGE/AUDIO/FILE/LINK 블록은 url을 사용한다.
     */
    private List<TimeLetterBlock> buildBlocks(Long userId, List<TimeLetterBlockRequest> blockRequests) {
        if (blockRequests == null || blockRequests.isEmpty()) {
            return new ArrayList<>();
        }

        validateBlocks(blockRequests);

        return blockRequests.stream()
                .map(req -> TimeLetterBlock.builder()
                        .blockType(req.getBlockType())
                        .blockOrder(req.getBlockOrder())
                        .textContent(req.getTextContent())
                        .url(normalizeBlockUrl(userId, req.getBlockType(), req.getUrl()))
                        .mimeType(req.getMimeType())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 본문 블록 검증
     * - blockType과 blockOrder는 필수이다.
     * - blockOrder는 1 이상의 양수여야 한다.
     * - 같은 타임레터 안에서 blockOrder는 중복될 수 없다.
     * - TEXT 블록은 textContent가 필요하다.
     * - TEXT가 아닌 블록은 url이 필요하다.
     */
    private void validateBlocks(List<TimeLetterBlockRequest> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        Set<Integer> orders = new HashSet<>();

        for (TimeLetterBlockRequest block : blocks) {
            if (block.getBlockType() == null || block.getBlockOrder() == null) {
                throw new CustomException(ErrorCode.TIME_LETTER_BLOCK_REQUIRED_FIELDS);
            }

            if (block.getBlockOrder() <= 0) {
                throw new CustomException(ErrorCode.TIME_LETTER_BLOCK_ORDER_INVALID);
            }

            if (!orders.add(block.getBlockOrder())) {
                throw new CustomException(ErrorCode.TIME_LETTER_BLOCK_ORDER_DUPLICATED);
            }

            if (block.getBlockType() == TimeLetterBlockType.TEXT) {
                if (block.getTextContent() == null || block.getTextContent().isBlank()) {
                    throw new CustomException(ErrorCode.TIME_LETTER_BLOCK_CONTENT_REQUIRED);
                }
            } else {
                if (block.getUrl() == null || block.getUrl().isBlank()) {
                    throw new CustomException(ErrorCode.TIME_LETTER_BLOCK_CONTENT_REQUIRED);
                }
            }
        }
    }

    /**
     * 요청 blocks 기준으로 본문 내용 존재 여부 확인
     * - TEXT 블록은 textContent가 있으면 본문이 있다고 판단한다.
     * - IMAGE/AUDIO/FILE/LINK 블록은 url이 있으면 본문이 있다고 판단한다.
     */
    private boolean hasContentBlock(List<TimeLetterBlockRequest> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }

        return blocks.stream().anyMatch(block -> {
            if (block.getBlockType() == TimeLetterBlockType.TEXT) {
                return block.getTextContent() != null && !block.getTextContent().isBlank();
            }

            return block.getUrl() != null && !block.getUrl().isBlank();
        });
    }

    /**
     * 엔티티 blocks 기준으로 본문 내용 존재 여부 확인
     * - 수정 요청에서 blocks가 전달되지 않은 경우 기존 blocks를 기준으로 검사한다.
     */
    private boolean hasContentBlockFromEntity(List<TimeLetterBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return false;
        }

        return blocks.stream().anyMatch(block -> {
            if (block.getBlockType() == TimeLetterBlockType.TEXT) {
                return block.getTextContent() != null && !block.getTextContent().isBlank();
            }

            return block.getUrl() != null && !block.getUrl().isBlank();
        });
    }

    /**
     * 블록 URL 정규화
     * - IMAGE/AUDIO/FILE은 staging/legacy를 permanent로 승격한 storage key로 저장한다.
     * - LINK는 외부 URL이므로 그대로 저장한다.
     */
    private String normalizeBlockUrl(Long userId, TimeLetterBlockType blockType, String rawUrlOrKey) {
        if (rawUrlOrKey == null || rawUrlOrKey.isBlank()) {
            return rawUrlOrKey;
        }

        if (blockType == TimeLetterBlockType.LINK) {
            return rawUrlOrKey;
        }

        return s3Service.promoteMediaKey("timeletters", userId, rawUrlOrKey);
    }
}
