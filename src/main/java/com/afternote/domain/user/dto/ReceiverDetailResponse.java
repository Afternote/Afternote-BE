package com.afternote.domain.user.dto;

import com.afternote.domain.receiver.model.Receiver;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Builder
public record ReceiverDetailResponse(
        @Schema(description = "수신인 ID", example = "1")
        @Getter
        Long receiverId,

        @Schema(description = "수신인 이름", example = "김소희")
        @Getter
        String name,

        @Schema(description = "수신인과의 관계", example = "딸")
        @Getter
        String relation,

        @Schema(description = "전화번호", example = "010-1234-1234", nullable = true)
        @Getter
        String phone,

        @Schema(description = "이메일", example = "afternote123@naver.com", nullable = true)
        @Getter
        String email,

        @Schema(description = "데일리 질문 답변 개수", example = "8")
        @Getter
        Integer dailyQuestionCount,

        @Schema(description = "타임레터 개수", example = "12")
        @Getter
        Integer timeLetterCount,

        @Schema(description = "애프터노트 개수", example = "4")
        @Getter
        Integer afterNoteCount,

        @Schema(description = "수신자에게 남긴 메시지", example = "사랑하는 딸에게...", nullable = true)
        @Getter
        String message,

        @Schema(description = "수신자 인증번호", example = "550e8400-e29b-41d4-a716-446655440000")
        @Getter
        String authCode
) {











    public static ReceiverDetailResponse from(
            Receiver receiver,
            int dailyQuestionCount,
            int timeLetterCount,
            int afterNoteCount
    ) {
        return ReceiverDetailResponse.builder()
                .receiverId(receiver.getId())
                .name(receiver.getName())
                .relation(receiver.getRelation())
                .phone(receiver.getPhone())
                .email(receiver.getEmail())
                .message(receiver.getMessage())
                .dailyQuestionCount(dailyQuestionCount)
                .timeLetterCount(timeLetterCount)
                .afterNoteCount(afterNoteCount)
                .authCode(receiver.getAuthCode())
                .build();
    }
}