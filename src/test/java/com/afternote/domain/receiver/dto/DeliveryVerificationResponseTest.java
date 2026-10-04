package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.DeliveryVerification;
import com.afternote.domain.receiver.model.VerificationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryVerificationResponseTest {

    @Test
    @DisplayName("승인 전에는 approvedAt이 null이고 생성 시각은 그대로 내려간다")
    void pending_ApprovedAtIsNull() {
        DeliveryVerification verification = verification();

        DeliveryVerificationResponse response = DeliveryVerificationResponse.from(verification);

        assertThat(response.status()).isEqualTo(VerificationStatus.PENDING);
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 21, 3, 7, 26));
        assertThat(response.approvedAt()).isNull();
    }

    @Test
    @DisplayName("승인 상태의 approvedAt은 기록함과 같이 updatedAt이다")
    void approved_ApprovedAtUsesUpdatedAt() {
        DeliveryVerification verification = verification();
        verification.approve("서류 확인 완료");

        DeliveryVerificationResponse response = DeliveryVerificationResponse.from(verification);

        assertThat(response.status()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(response.approvedAt()).isEqualTo(LocalDateTime.of(2026, 7, 29, 16, 0, 10));
        assertThat(response.adminNote()).isEqualTo("서류 확인 완료");
    }

    @Test
    @DisplayName("거절 상태에는 approvedAt을 채우지 않는다")
    void rejected_ApprovedAtIsNull() {
        DeliveryVerification verification = verification();
        verification.reject("서류 불일치");

        assertThat(DeliveryVerificationResponse.from(verification).approvedAt()).isNull();
    }

    private static DeliveryVerification verification() {
        DeliveryVerification verification = DeliveryVerification.builder()
                .userId(1L)
                .receiverId(7L)
                .deathCertificateUrl("documents/death")
                .familyRelationCertificateUrl("documents/family")
                .build();
        ReflectionTestUtils.setField(verification, "id", 4L);
        ReflectionTestUtils.setField(verification, "createdAt", LocalDateTime.of(2026, 6, 21, 3, 7, 26));
        ReflectionTestUtils.setField(verification, "updatedAt", LocalDateTime.of(2026, 7, 29, 16, 0, 10));
        return verification;
    }
}
