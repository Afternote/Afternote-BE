package com.afternote.domain.receiver.model;

import com.afternote.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "delivery_verification")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DeliveryVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long receiverId;

    @Column(nullable = false)
    private String deathCertificateUrl;

    @Column(nullable = false)
    private String familyRelationCertificateUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus status;

    @Column
    private String adminNote;

    @Builder
    public DeliveryVerification(Long userId, Long receiverId, String deathCertificateUrl,
                                 String familyRelationCertificateUrl) {
        this.userId = userId;
        this.receiverId = receiverId;
        this.deathCertificateUrl = deathCertificateUrl;
        this.familyRelationCertificateUrl = familyRelationCertificateUrl;
        this.status = VerificationStatus.PENDING;
    }

    public void approve(String note) {
        this.status = VerificationStatus.APPROVED;
        this.adminNote = note;
    }

    public void reject(String note) {
        this.status = VerificationStatus.REJECTED;
        this.adminNote = note;
    }
}
