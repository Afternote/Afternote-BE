package com.afternote.domain.auth.service.social;

import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleLoginServiceTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "not-a-jwt", "a.b", "a.b.", ".b.c", "a..c", "onlyone"})
    @DisplayName("빈 값·JWT 형태가 아니면 SOCIAL_LOGIN_FAILED")
    void requireLooksLikeGoogleIdToken_RejectsMalformed(String token) {
        assertThatThrownBy(() -> GoogleLoginService.requireLooksLikeGoogleIdToken(token))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SOCIAL_LOGIN_FAILED));
    }

    @Test
    @DisplayName("header.payload.signature 형태면 선검증 통과")
    void requireLooksLikeGoogleIdToken_AcceptsThreeParts() {
        GoogleLoginService.requireLooksLikeGoogleIdToken("aaa.bbb.ccc");
    }
}
