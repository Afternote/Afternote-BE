package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.DeliveryVerification;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.ReceivedRecordStatus;
import com.afternote.domain.receiver.model.VerificationStatus;
import com.afternote.domain.user.model.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "받은 기록함 응답")
public record ReceivedRecordBoxResponse(

        @Schema(description = "수신자 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long receiverId,

        @Schema(
                description = "기록 열람 시 X-Auth-Code에 넣을 접근 코드",
                example = "550e8400-e29b-41d4-a716-446655440000",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String accessCode,

        @Schema(description = "발신자 이름", example = "김혜성", requiredMode = Schema.RequiredMode.REQUIRED)
        String senderName,

        @Schema(description = "수신자 이름", example = "김지은", requiredMode = Schema.RequiredMode.REQUIRED)
        String receiverName,

        @Schema(
                description = RELATION_DESCRIPTION,
                example = "DAUGHTER",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String relation,

        @Schema(description = "기록 보관 상태. STORED 또는 EMPTY", example = "STORED", requiredMode = Schema.RequiredMode.REQUIRED)
        ReceivedRecordStatus recordStatus,

        @Schema(
                description = "열람 상태. VIEWABLE(조건 충족)·PENDING(서류 검토 중)·REQUESTABLE(열람 신청 가능)",
                example = "VIEWABLE",
                allowableValues = {"VIEWABLE", "PENDING", "REQUESTABLE"},
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String viewStatus,

        @Schema(
                description = VERIFICATION_STATUS_DESCRIPTION,
                example = "PENDING",
                allowableValues = {"PENDING", "APPROVED", "REJECTED"},
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String verificationStatus,

        @Schema(
                description = REQUESTED_AT_DESCRIPTION,
                example = LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = LOCAL_DATE_TIME_FORMAT,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        LocalDateTime requestedAt,

        @Schema(
                description = APPROVED_AT_DESCRIPTION,
                example = LOCAL_DATE_TIME_EXAMPLE,
                type = "string",
                format = LOCAL_DATE_TIME_FORMAT,
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        LocalDateTime approvedAt
) {
    public static final String RELATION_DESCRIPTION =
            "발신자와 수신자의 관계. DB가 null을 허용하므로 미입력 시 null";
    public static final String VERIFICATION_STATUS_DESCRIPTION =
            "서류 인증 상태. 열람 신청이 없으면 null";
    public static final String REQUESTED_AT_DESCRIPTION =
            "열람 신청일. 열람 신청이 없으면 null. 오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)";
    public static final String APPROVED_AT_DESCRIPTION =
            "열람 승인일. 인증 상태가 APPROVED일 때만 채워지고, 그 외·열람 신청이 없으면 null. "
                    + "오프셋 없는 ISO-8601 로컬 시각(Asia/Seoul)";
    public static final String LOCAL_DATE_TIME_EXAMPLE = "2026-08-25T18:43:47.696636";
    public static final String LOCAL_DATE_TIME_FORMAT = "local-date-time";

    public static ReceivedRecordBoxResponse from(
            Receiver receiver,
            User sender,
            DeliveryVerification verification,
            ReceivedRecordStatus recordStatus,
            boolean anyConditionFulfilled
    ) {
        String verificationStatus = verification != null
                ? verification.getStatus().name()
                : null;

        LocalDateTime requestedAt = verification != null
                ? verification.getCreatedAt()
                : null;

        LocalDateTime approvedAt = verification != null
                && verification.getStatus() == VerificationStatus.APPROVED
                ? verification.getUpdatedAt()
                : null;

        return new ReceivedRecordBoxResponse(
                receiver.getId(),
                receiver.getAuthCode(),
                sender.getName(),
                receiver.getName(),
                receiver.getRelation(),
                recordStatus,
                determineViewStatus(anyConditionFulfilled, verification),
                verificationStatus,
                requestedAt,
                approvedAt
        );
    }

    private static String determineViewStatus(boolean anyConditionFulfilled, DeliveryVerification verification) {
        if (anyConditionFulfilled) {
            return "VIEWABLE";
        }

        if (verification != null && verification.getStatus() == VerificationStatus.PENDING) {
            return "PENDING";
        }

        return "REQUESTABLE";
    }
}
