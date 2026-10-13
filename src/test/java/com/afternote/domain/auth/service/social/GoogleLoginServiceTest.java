package com.afternote.domain.auth.service.social;

import com.afternote.global.exception.CustomException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.provider.NullSource;
import static org.mockito.Mockito.mock;
import static org.mockito.BDDMockito.given;
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
    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    void unverifiedEmailCannotBeUsedForAccountMatching(Boolean verified) throws Exception {
        GoogleLoginService service = serviceWithEmailVerification(verified);
        assertThatThrownBy(() -> service.getUserInfo("aaa.bbb.ccc"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SOCIAL_LOGIN_FAILED));
    }

    @Test
    void verifiedEmailIsAvailableForAccountMatching() throws Exception {
        assertThat(serviceWithEmailVerification(true).getUserInfo("aaa.bbb.ccc").getEmail())
                .isEqualTo("verified@test.com");
    }

    private GoogleLoginService serviceWithEmailVerification(Boolean verified) throws Exception {
        GoogleLoginService service = new GoogleLoginService("client-id");
        GoogleIdTokenVerifier verifier = mock(GoogleIdTokenVerifier.class);
        GoogleIdToken token = mock(GoogleIdToken.class);
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-1");
        payload.setEmail("verified@test.com");
        payload.setEmailVerified(verified);
        given(token.getPayload()).willReturn(payload);
        given(verifier.verify("aaa.bbb.ccc")).willReturn(token);
        ReflectionTestUtils.setField(service, "verifier", verifier);
        return service;
    }

}
