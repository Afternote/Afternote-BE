package com.afternote.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

public record UserCreateReceiverRequest(
        @Schema(description = "수신자 이름", example = "김지은")
        @NotBlank
        @Getter
        String name,

        @Schema(description = "사용자와의 관계", example = "DAUGHTER")
        @NotBlank
        @Getter
        String relation,

        @Schema(description = "전화번호 (선택, 입력 시 국내 휴대폰 형식)", example = "010-1234-5678", nullable = true)
        @Getter
        String phone,

        @Schema(description = "이메일", example = "jieun@naver.com", nullable = true)
        @Getter
        String email,

        @Schema(description = "수신자에게 남길 메시지", example = "사랑하는 딸에게...", nullable = true)
        @Getter
        String message
) {





}