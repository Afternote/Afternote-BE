package com.afternote.domain.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserMarketingConsentTest {

    @Test
    @DisplayName("가입 시 마케팅 동의는 모두 거부(opt-in)")
    void defaultsToOptInFalse() {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        assertThat(user.isMarketingSmsEnabled()).isFalse();
        assertThat(user.isMarketingEmailEnabled()).isFalse();
        assertThat(user.isMarketingPushEnabled()).isFalse();
        assertThat(user.isTimeLetterPushEnabled()).isTrue();
    }

    @Test
    @DisplayName("보낸 채널만 바꾸고 나머지는 유지한다")
    void updatePartial() {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        user.updateMarketingConsents(true, null, null);

        assertThat(user.isMarketingSmsEnabled()).isTrue();
        assertThat(user.isMarketingEmailEnabled()).isFalse();
        assertThat(user.isMarketingPushEnabled()).isFalse();
    }
}
