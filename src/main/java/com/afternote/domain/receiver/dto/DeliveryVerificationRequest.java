package com.afternote.domain.receiver.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "사망확인 서류 제출 요청 (두 서류 중 하나 이상 필수)")
public class DeliveryVerificationRequest {

    @Schema(description = "사망진단서 URL", example = "https://bucket.s3.region.amazonaws.com/documents/xxx.pdf", nullable = true)
    private String deathCertificateUrl;

    @Schema(description = "가족관계증명서 URL", example = "https://bucket.s3.region.amazonaws.com/documents/yyy.pdf", nullable = true)
    private String familyRelationCertificateUrl;
}
