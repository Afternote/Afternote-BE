package com.afternote.domain.subscription.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.CreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 결제사 콜백/복원 재시도의 멱등 키를 영구 보관한다. 실제 쓰기는 INSERT IGNORE로 수행한다.
 */
@Entity
@Table(
        name = "subscription_entitlement_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_subscription_entitlement_events_provider_event",
                columnNames = {"provider", "event_id"}
        ),
        indexes = @Index(
                name = "idx_subscription_entitlement_events_user_occurred",
                columnList = "user_id, occurred_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionEntitlementEvent extends CreatedAtEntity {

    /**
     * 서버가 이벤트 기록을 식별하기 위해 생성하는 내부 기본키다.
     * 결제사가 전달한 {@link #eventId}와는 다른 값이다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이벤트가 귀속되는 Afternote 사용자다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    /** 이벤트를 발급하고 검증한 결제 시스템이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntitlementProvider provider;

    /**
     * 같은 결제사 안에서 이벤트를 유일하게 식별하는 값이다.
     * {@link #provider}와의 조합을 이벤트 중복 처리 방지 키로 사용한다.
     */
    @Column(name = "event_id", nullable = false, length = 191)
    private String eventId;

    /** 이 이벤트가 부여하거나 변경하는 제품 이용 권한의 종류다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private EntitlementFeature feature;

    /** 권한 부여, 갱신, 만료, 회수 등 결제사가 알린 상태 변화의 종류다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EntitlementEventType eventType;

    /**
     * 결제사가 구매 또는 구독 건을 식별하기 위해 발급한 값이며, 없을 수 있다.
     * 예: Google Play purchaseToken
     */
    @Column(name = "external_reference", length = 191)
    private String externalReference;

    /**
     * 결제사 기준으로 이벤트가 발생한 시각이다.
     * 이벤트가 역순으로 도착할 때 현재 권한에 반영할 이벤트를 고르는 1차 정렬 기준이다.
     */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** 권한 효력이 시작되는 시각이며, 없으면 {@link #occurredAt}을 시작 시각으로 사용한다. */
    @Column(name = "effective_from")
    private LocalDateTime effectiveFrom;

    /** 권한 효력이 끝나는 시각이며, 만료 시각이 정해지지 않은 활성 권한은 없을 수 있다. */
    @Column(name = "effective_until", nullable = false)
    private LocalDateTime effectiveUntil;
}
