package com.afternote.domain.delivery.service;

import com.afternote.domain.delivery.dto.DeliveryConditionItemRequest;
import com.afternote.domain.delivery.dto.ReceiverDeliveryConditionResponse;
import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryConditionService {

    private final DeliveryConditionRepository deliveryConditionRepository;
    private final ReceiverRepository receiverRepository;
    private final UserRepository userRepository;
    private final DeliveryNotificationService deliveryNotificationService;

    /**
     * 수신자별 전달 조건 목록 조회
     */
    public ReceiverDeliveryConditionResponse getConditions(Long userId, Long receiverId) {
        validateReceiverOwnership(userId, receiverId);
        return ReceiverDeliveryConditionResponse.from(
                receiverId,
                deliveryConditionRepository.findByReceiverId(receiverId)
        );
    }

    /**
     * 수신자별 콘텐츠 타입별 전달 조건 설정/변경.
     * - 기존 서류/검증은 유지하되, 조건 타입이 바뀌면 상태를 초기화한다.
     */
    @Transactional
    public ReceiverDeliveryConditionResponse updateConditions(
            Long userId, Long receiverId, List<DeliveryConditionItemRequest> items) {
        validateReceiverOwnership(userId, receiverId);

        for (DeliveryConditionItemRequest item : items) {
            upsertCondition(userId, receiverId, item);
        }

        return ReceiverDeliveryConditionResponse.from(
                receiverId,
                deliveryConditionRepository.findByReceiverId(receiverId)
        );
    }

    private void upsertCondition(Long userId, Long receiverId, DeliveryConditionItemRequest item) {
        DeliveryCondition condition = deliveryConditionRepository
                .findByReceiverIdAndContentType(receiverId, item.getContentType())
                .orElse(null);

        if (condition == null) {
            condition = DeliveryCondition.builder()
                    .userId(userId)
                    .receiverId(receiverId)
                    .contentType(item.getContentType())
                    .conditionType(item.getConditionType())
                    .inactivityPeriod(item.getInactivityPeriod())
                    .build();
            deliveryConditionRepository.save(condition);
            return;
        }

        // 이미 충족된 조건이고 타입이 동일하면 유지한다.
        boolean sameType = condition.getConditionType() == item.getConditionType();
        if (condition.isFulfilled() && sameType) {
            return;
        }

        condition.applyConditionType(item.getConditionType(), item.getInactivityPeriod());
    }

    /**
     * 특정 (수신자, 콘텐츠) 조건이 충족되었는지 확인하고, 아니면 예외를 던진다.
     */
    public void requireFulfilled(Long receiverId, DeliveryContentType contentType) {
        if (!isFulfilled(receiverId, contentType)) {
            throw new CustomException(ErrorCode.DELIVERY_CONDITION_NOT_MET);
        }
    }

    public boolean isFulfilled(Long receiverId, DeliveryContentType contentType) {
        return deliveryConditionRepository
                .findByReceiverIdAndContentType(receiverId, contentType)
                .map(DeliveryCondition::isFulfilled)
                .orElse(false);
    }

    public boolean hasAnyFulfilled(Long receiverId) {
        return deliveryConditionRepository.existsByReceiverIdAndState(receiverId, ConditionState.FULFILLED);
    }

    /**
     * 운영자 승인(수신자 요청) 시, 해당 (발신자, 수신자)의 RECEIVER_REQUEST 조건을 모두 충족 처리한다.
     */
    @Transactional
    public void fulfillByReceiverRequest(Long userId, Long receiverId) {
        List<DeliveryCondition> conditions = deliveryConditionRepository
                .findByUserIdAndReceiverIdAndConditionType(
                        userId, receiverId, DeliveryConditionType.RECEIVER_REQUEST);

        if (conditions.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        for (DeliveryCondition condition : conditions) {
            if (!condition.isFulfilled()) {
                condition.fulfill(now);
                changed = true;
            }
        }

        if (changed) {
            notifyReceiverDelivered(userId, receiverId);
        }
    }

    private void notifyReceiverDelivered(Long userId, Long receiverId) {
        Receiver receiver = receiverRepository.findById(receiverId).orElse(null);
        User sender = userRepository.findById(userId).orElse(null);
        if (receiver == null) {
            return;
        }
        deliveryNotificationService.sendDeliveredToReceiver(
                receiver.getEmail(),
                receiver.getName(),
                sender != null ? sender.getName() : null
        );
    }

    private void validateReceiverOwnership(Long userId, Long receiverId) {
        Receiver receiver = receiverRepository.findById(receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));
        if (!receiver.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_ENOUGH_PERMISSION);
        }
    }
}
