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

        @Schema(description = "수신자 ID", example = "1")
        Long receiverId,

        @Schema(description = "기록 열람 시 X-Auth-Code에 넣을 접근 코드", example = "550e8400-e29b-41d4-a716-446655440000")
        String accessCode,

        @Schema(description = "발신자 이름", example = "김혜성")
        String senderName,

        @Schema(description = "수신자 이름", example = "김지은")
        String receiverName,

        @Schema(description = "발신자와 수신자의 관계", example = "DAUGHTER")
        String relation,

        @Schema(description = "기록 보관 상태", example = "STORED")
        ReceivedRecordStatus recordStatus,

        @Schema(description = "열람 상태", example = "VIEWABLE")
        String viewStatus,

        @Schema(description = "서류 인증 상태", example = "PENDING")
        String verificationStatus,

        @Schema(description = "열람 신청일", example = "2026-05-03T10:30:00")
        LocalDateTime requestedAt,

        @Schema(description = "열람 승인일", example = "2026-05-03T14:20:00")
        LocalDateTime approvedAt
) {
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