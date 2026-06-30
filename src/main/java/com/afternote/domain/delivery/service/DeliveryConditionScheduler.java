package com.afternote.domain.delivery.service;

import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 미사용 자동 전달 조건의 상태 전환을 처리하는 스케줄러.
 * - 미사용 기간 도달: ACTIVE -> PENDING_CONFIRMATION + 본인확인 이메일
 * - 7일 유예 만료: PENDING_CONFIRMATION -> FULFILLED + 수신자 이메일
 * - 유예 중 활동 복귀: PENDING_CONFIRMATION -> ACTIVE
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryConditionScheduler {

    private static final int GRACE_PERIOD_DAYS = 7;

    private final DeliveryConditionRepository deliveryConditionRepository;
    private final UserRepository userRepository;
    private final ReceiverRepository receiverRepository;
    private final DeliveryNotificationService deliveryNotificationService;

    /**
     * 1시간마다 미사용 조건을 평가한다.
     */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void evaluateInactivityConditions() {
        LocalDateTime now = LocalDateTime.now();
        detectInactivity(now);
        expireGracePeriod(now);
    }

    private void detectInactivity(LocalDateTime now) {
        List<DeliveryCondition> conditions = deliveryConditionRepository
                .findByConditionTypeAndState(DeliveryConditionType.INACTIVITY, ConditionState.ACTIVE);
        if (conditions.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = loadUsers(conditions);
        Set<Long> ownersToNotify = new HashSet<>();

        for (DeliveryCondition condition : conditions) {
            User user = userMap.get(condition.getUserId());
            if (user == null || condition.getInactivityPeriod() == null) {
                continue;
            }

            LocalDateTime lastActive = user.getLastActiveAt();
            if (lastActive == null) {
                lastActive = now;
            }

            LocalDateTime threshold = lastActive.plusDays(condition.getInactivityPeriod().getDays());
            if (now.isAfter(threshold)) {
                condition.markPendingConfirmation(now);
                ownersToNotify.add(condition.getUserId());
            }
        }

        // 동일 유저는 본인확인 이메일을 1회만 발송한다.
        for (Long ownerId : ownersToNotify) {
            User user = userMap.get(ownerId);
            if (user != null) {
                deliveryNotificationService.sendConfirmationToOwner(user.getEmail(), user.getName());
            }
        }

        if (!ownersToNotify.isEmpty()) {
            log.info("미사용 감지: {}명에게 본인확인 알림 발송", ownersToNotify.size());
        }
    }

    private void expireGracePeriod(LocalDateTime now) {
        List<DeliveryCondition> conditions = deliveryConditionRepository
                .findByConditionTypeAndState(DeliveryConditionType.INACTIVITY, ConditionState.PENDING_CONFIRMATION);
        if (conditions.isEmpty()) {
            return;
        }

        Map<Long, User> userMap = loadUsers(conditions);
        int fulfilledCount = 0;

        for (DeliveryCondition condition : conditions) {
            User user = userMap.get(condition.getUserId());
            LocalDateTime graceStart = condition.getGracePeriodStartedAt();
            if (user == null || graceStart == null) {
                continue;
            }

            // 유예 시작 이후 활동이 감지되면 복귀
            if (user.getLastActiveAt() != null && user.getLastActiveAt().isAfter(graceStart)) {
                condition.resetToActive();
                continue;
            }

            if (now.isAfter(graceStart.plusDays(GRACE_PERIOD_DAYS))) {
                condition.fulfill(now);
                notifyReceiver(condition, user);
                fulfilledCount++;
            }
        }

        if (fulfilledCount > 0) {
            log.info("미사용 유예 만료: {}건 전달 조건 충족 처리", fulfilledCount);
        }
    }

    private void notifyReceiver(DeliveryCondition condition, User sender) {
        Receiver receiver = receiverRepository.findById(condition.getReceiverId()).orElse(null);
        if (receiver == null) {
            return;
        }
        deliveryNotificationService.sendDeliveredToReceiver(
                receiver.getEmail(),
                receiver.getName(),
                sender != null ? sender.getName() : null
        );
    }

    private Map<Long, User> loadUsers(List<DeliveryCondition> conditions) {
        Set<Long> userIds = conditions.stream()
                .map(DeliveryCondition::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
