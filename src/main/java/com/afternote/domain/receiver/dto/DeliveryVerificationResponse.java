package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.DeliveryVerification;
import com.afternote.domain.receiver.model.VerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.function.Function;

@Schema(description = "사망확인 서류 제출 응답")
public record DeliveryVerificationResponse(
        @Schema(description = "인증 요청 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(description = "인증 상태", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
        VerificationStatus status,
        @Schema(
                description = "사망진단서 URL. 해당 서류를 제출하지 않았으면 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String deathCertificateUrl,
        @Schema(
                description = "가족관계증명서 URL. 해당 서류를 제출하지 않았으면 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String familyRelationCertificateUrl,
        @Schema(
                description = "관리자 메모. 검토 전이면 null",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String adminNote,
        @Schema(
                description = "생성일시. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)",
                example = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = ReceivedRecordBoxResponse.LOCAL_DATE_TIME_FORMAT,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        LocalDateTime createdAt
) {
    public static DeliveryVerificationResponse from(DeliveryVerification verification) {
        return from(verification, Function.identity());
    }

    public static DeliveryVerificationResponse from(DeliveryVerification verification, Function<String, String> urlResolver) {
        return new DeliveryVerificationResponse(
                verification.getId(),
                verification.getStatus(),
                urlResolver.apply(verification.getDeathCertificateUrl()),
                urlResolver.apply(verification.getFamilyRelationCertificateUrl()),
                verification.getAdminNote(),
                verification.getCreatedAt()
        );
    }
}
