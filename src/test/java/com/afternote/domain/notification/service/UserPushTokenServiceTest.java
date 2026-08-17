package com.afternote.domain.notification.service;

import com.afternote.domain.notification.dto.DeletePushTokenRequest;
import com.afternote.domain.notification.dto.RegisterPushTokenRequest;
import com.afternote.domain.notification.model.PushPlatform;
import com.afternote.domain.notification.model.UserPushToken;
import com.afternote.domain.notification.repository.UserPushTokenRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserPushTokenServiceTest {

    @InjectMocks
    private UserPushTokenService userPushTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPushTokenRepository userPushTokenRepository;

    @Test
    @DisplayName("새 FCM token을 등록한다")
    void registerOrRefresh_CreatesNewToken() {
        User user = user(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userPushTokenRepository.findByToken("token-a")).willReturn(Optional.empty());
        given(userPushTokenRepository.save(any(UserPushToken.class))).willAnswer(invocation -> invocation.getArgument(0));

        var response = userPushTokenService.registerOrRefresh(
                1L,
                new RegisterPushTokenRequest("token-a", PushPlatform.ANDROID)
        );

        assertThat(response.token()).isEqualTo("token-a");
        assertThat(response.platform()).isEqualTo(PushPlatform.ANDROID);
        assertThat(response.lastSeenAt()).isNotNull();

        ArgumentCaptor<UserPushToken> captor = ArgumentCaptor.forClass(UserPushToken.class);
        verify(userPushTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("다른 사용자에 묶인 token은 현재 사용자로 재할당한다")
    void registerOrRefresh_ReassignsTokenFromOtherUser() {
        User currentUser = user(2L);
        User previousUser = user(1L);
        UserPushToken existing = UserPushToken.builder()
                .user(previousUser)
                .token("token-a")
                .platform(PushPlatform.ANDROID)
                .lastSeenAt(java.time.LocalDateTime.now().minusDays(1))
                .build();

        given(userRepository.findById(2L)).willReturn(Optional.of(currentUser));
        given(userPushTokenRepository.findByToken("token-a")).willReturn(Optional.of(existing));

        var response = userPushTokenService.registerOrRefresh(
                2L,
                new RegisterPushTokenRequest("token-a", PushPlatform.IOS)
        );

        assertThat(response.platform()).isEqualTo(PushPlatform.IOS);
        assertThat(existing.getUser()).isEqualTo(currentUser);
        verify(userPushTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("token 해제는 멱등하게 동작한다")
    void unregister_DeletesByUserAndToken() {
        userPushTokenService.unregister(1L, new DeletePushTokenRequest("token-a"));
        verify(userPushTokenRepository).deleteByUser_IdAndToken(1L, "token-a");
    }

    private static User user(long id) {
        User user = User.builder()
                .email("u" + id + "@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
