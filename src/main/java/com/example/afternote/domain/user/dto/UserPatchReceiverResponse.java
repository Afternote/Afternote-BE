package com.example.afternote.domain.user.dto;

import com.example.afternote.domain.receiver.model.Receiver;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserPatchReceiverResponse {

    @Schema(description = "수신인 ID", example = "1")
    private Long receiverId;

    @Schema(description = "이름", example = "김소희")
    private String name;

    @Schema(description = "전화번호", example = "010-1234-5678")
    private String phone;

    @Schema(description = "관계", example = "DAUGHTER")
    private String relation;

    @Schema(description = "이메일", example = "sohee@example.com")
    private String email;


    public static UserPatchReceiverResponse from(Receiver receiver) {
        return UserPatchReceiverResponse.builder()
                .receiverId(receiver.getId())
                .name(receiver.getName())
                .phone(receiver.getPhone())
                .relation(receiver.getRelation())
                .email(receiver.getEmail())
                .build();
    }
}
