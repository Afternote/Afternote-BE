package com.afternote.domain.subscription.model;

import com.afternote.domain.user.model.User;
import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 사용자와 기능별 현재 이용 권한을 저장한 스냅샷이다.
 * 이벤트 이력 중 {@code lastEventOccurredAt, lastEventId} 순서가 가장 최신인 이벤트만 반영한다.
 */
@Entity
@Table(
        name = "user_entitlements",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_entitlements_user_feature",
                columnNames = {"user_id", "feature"}
        ),
        indexes = @Index(
                name = "idx_user_entitlements_active",
                columnList = "feature, status, expires_at"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntitlement extends BaseEntity {

    /** 현재 권한 스냅샷의 내부 기본키다. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 이용 권한을 보유한 Afternote 사용자다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    /** 사용자에게 허용되는 제품 기능의 종류다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private EntitlementFeature feature;

    /** 최신 결제 이벤트를 반영한 현재 권한 상태다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntitlementStatus status;

    /** 현재 상태를 결정한 이벤트를 발급하고 검증한 결제 시스템이다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EntitlementProvider provider;

    /**
     * 결제사가 구매 또는 구독 건을 식별하기 위해 발급한 값이며, 없을 수 있다.
     * 예: Google Play purchaseToken
     */
    @Column(name = "external_reference", length = 191)
    private String externalReference;

    /** 권한이 유효해지는 시작 시각으로, 이 시각을 포함한다. */
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    /** 권한이 더 이상 유효하지 않은 만료 시각으로, 이 시각은 포함하지 않으며 없으면 만료되지 않는다. */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 현재 스냅샷에 반영된 마지막 결제사 이벤트 ID다.
     * 발생 시각이 같으면 DB 문자열 비교 결과가 더 큰 이벤트 ID를 최신으로 판단한다.
     */
    @Column(name = "last_event_id", nullable = false, length = 191)
    private String lastEventId;

    /** 현재 스냅샷에 반영된 마지막 이벤트의 결제사 기준 발생 시각이다. */
    @Column(name = "last_event_occurred_at", nullable = false)
    private LocalDateTime lastEventOccurredAt;

    @Builder
    public UserEntitlement(
            User user,
            EntitlementFeature feature,
            EntitlementStatus status,
            EntitlementProvider provider,
            String externalReference,
            LocalDateTime startsAt,
            LocalDateTime expiresAt,
            String lastEventId,
            LocalDateTime lastEventOccurredAt
    ) {
        this.user = user;
        this.feature = feature;
        this.status = status;
        this.provider = provider;
        this.externalReference = externalReference;
        this.startsAt = startsAt;
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.lastEventId = lastEventId;
        this.lastEventOccurredAt = lastEventOccurredAt;
    }

    public boolean isActiveAt(LocalDateTime now) {
        return status == EntitlementStatus.ACTIVE
                && !startsAt.isAfter(now)
                && expiresAt.isAfter(now);
    }
}
