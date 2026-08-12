package com.afternote.domain.delivery.model;

import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.global.common.BaseEntity;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 수신자 x 콘텐츠 타입 단위의 사후 전달 조건.
 * - 타임레터(POST_DEATH 모드), 애프터노트, 데일리질문, 다이어리, 딥쏘트에 적용된다.
 */
@Entity
@Table(
        name = "delivery_condition",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_delivery_condition_receiver_content",
                columnNames = {"receiver_id", "content_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryCondition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private DeliveryContentType contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false)
    private DeliveryConditionType conditionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "inactivity_period")
    private InactivityPeriod inactivityPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private ConditionState state;

    @Column(name = "grace_period_started_at")
    private LocalDateTime gracePeriodStartedAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @Builder
    public DeliveryCondition(Long userId, Long receiverId, DeliveryContentType contentType,
                             DeliveryConditionType conditionType, InactivityPeriod inactivityPeriod) {
        this.userId = userId;
        this.receiverId = receiverId;
        this.contentType = contentType;
        applyConditionType(conditionType, inactivityPeriod);
    }

    /**
     * 조건 타입/기간을 적용하고 상태를 초기화한다.
     * - INACTIVITY: ACTIVE 로 시작 (스케줄러가 미사용 감지)
     * - RECEIVER_REQUEST: WAITING_VERIFICATION 으로 시작 (운영자 승인 대기)
     */
    public void applyConditionType(DeliveryConditionType conditionType, InactivityPeriod inactivityPeriod) {
        if (conditionType == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        this.conditionType = conditionType;
        this.gracePeriodStartedAt = null;
        this.fulfilledAt = null;

        switch (conditionType) {
            case INACTIVITY -> {
                if (inactivityPeriod == null) {
                    throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
                }
                this.inactivityPeriod = inactivityPeriod;
                this.state = ConditionState.ACTIVE;
            }
            case RECEIVER_REQUEST -> {
                this.inactivityPeriod = null;
                this.state = ConditionState.WAITING_VERIFICATION;
            }
        }
    }

    public boolean isFulfilled() {
        return this.state == ConditionState.FULFILLED;
    }

    public void markPendingConfirmation(LocalDateTime now) {
        this.state = ConditionState.PENDING_CONFIRMATION;
        this.gracePeriodStartedAt = now;
    }

    public void resetToActive() {
        this.state = ConditionState.ACTIVE;
        this.gracePeriodStartedAt = null;
    }

    public void fulfill(LocalDateTime now) {
        this.state = ConditionState.FULFILLED;
        this.fulfilledAt = now;
    }
}
