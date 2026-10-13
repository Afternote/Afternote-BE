package com.afternote.domain.auth.service.social;

import com.afternote.domain.auth.dto.SocialUserInfo;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KakaoLoginServiceTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "", "\"email\":null,", "\"email\":\"\",",
            "\"email\":\"unsafe@test.com\",\"is_email_valid\":true,\"is_email_verified\":false,",
            "\"email\":\"unsafe@test.com\",\"is_email_valid\":false,\"is_email_verified\":true,",
            "\"email\":\"unsafe@test.com\","})
    void missingOrUnverifiedEmailCannotBeUsedForAccountMatching(String emailFields) {
        SocialUserInfo info = service("{\"id\":123,\"kakao_account\":{" + emailFields
                + "\"profile\":{\"nickname\":\"tester\"}}}").getUserInfo("token");
        assertThat(info.getProviderId()).isEqualTo("123");
        assertThat(info.getEmail()).isNullOrEmpty();
    }

    @Test
    void verifiedEmailIsAvailableForSignupAndLinking() {
        SocialUserInfo info = service("""
                {"id":123,"kakao_account":{"email":"safe@test.com","is_email_valid":true,
                "is_email_verified":true,"profile":{"nickname":"tester"}}}
                """).getUserInfo("token");
        assertThat(info.getEmail()).isEqualTo("safe@test.com");
    }

    @Test
    void missingProviderIdIsRejected() {
        assertThatThrownBy(() -> service("{\"kakao_account\":{}}").getUserInfo("token"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SOCIAL_LOGIN_FAILED));
    }

    private KakaoLoginService service(String json) {
        WebClient client = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK).header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(json).build())).build();
        KakaoLoginService service = new KakaoLoginService(client);
        ReflectionTestUtils.setField(service, "kakaoUserInfoUrl", "https://kakao.example/user");
        return service;
    }
}
