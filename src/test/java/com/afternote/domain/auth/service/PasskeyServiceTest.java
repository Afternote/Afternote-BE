package com.afternote.domain.auth.service;

import com.afternote.domain.auth.model.UserPasskey;
import com.afternote.domain.auth.repository.UserPasskeyRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.config.PasskeyProperties;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.converter.util.ObjectConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasskeyServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserPasskeyRepository userPasskeyRepository;
    @Mock PasskeyChallengeService passkeyChallengeService;
    @Mock WebAuthnManager webAuthnManager;
    @Mock AuthService authService;

    PasskeyService passkeyService;

    @BeforeEach
    void setUp() {
        passkeyService = new PasskeyService(
                userRepository,
                userPasskeyRepository,
                passkeyChallengeService,
                new PasskeyProperties(),
                webAuthnManager,
                new ObjectConverter(),
                new ObjectMapper(),
                authService
        );
    }

    @Test
    @DisplayName("잘못된 JSON 등록은 2701")
    void registerInvalidJson() {
        User user = sampleUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(webAuthnManager.parseRegistrationResponseJSON(any(String.class))).willThrow(new RuntimeException("bad json"));

        assertThatThrownBy(() -> passkeyService.register(1L, new ObjectMapper().readTree("{}")))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PASSKEY_VERIFICATION_FAILED);
    }

    @Test
    @DisplayName("목록은 최신순 응답으로 매핑한다")
    void list() {
        User user = sampleUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        UserPasskey passkey = UserPasskey.builder()
                .user(user)
                .credentialId(new byte[]{1})
                .attestedCredentialData(new byte[]{2})
                .signCount(0)
                .displayName("내 폰")
                .build();
        ReflectionTestUtils.setField(passkey, "id", 8L);
        given(userPasskeyRepository.findAllByUserOrderByIdDesc(user)).willReturn(List.of(passkey));

        assertThat(passkeyService.list(1L)).hasSize(1);
        assertThat(passkeyService.list(1L).get(0).displayName()).isEqualTo("내 폰");
    }

    @Test
    @DisplayName("마지막 로그인 수단인 패스키는 삭제할 수 없다")
    void deleteLastCredential() {
        User user = User.builder()
                .email("s@test.com")
                .password(null)
                .name("social")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(userRepository.findWithProvidersById(1L)).willReturn(Optional.of(user));
        UserPasskey passkey = UserPasskey.builder()
                .user(user)
                .credentialId(new byte[]{1})
                .attestedCredentialData(new byte[]{2})
                .signCount(0)
                .displayName("패스키")
                .build();
        ReflectionTestUtils.setField(passkey, "id", 4L);
        given(userPasskeyRepository.findById(4L)).willReturn(Optional.of(passkey));
        given(userPasskeyRepository.countByUser(user)).willReturn(1L);

        assertThatThrownBy(() -> passkeyService.delete(1L, 4L))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_UNLINK_LAST_CREDENTIAL);
    }

    @Test
    @DisplayName("비밀번호 있는 계정은 패스키를 지울 수 있다")
    void deleteWhenPasswordExists() {
        User user = sampleUser();
        given(userRepository.findWithProvidersById(1L)).willReturn(Optional.of(user));
        UserPasskey passkey = UserPasskey.builder()
                .user(user)
                .credentialId(new byte[]{1})
                .attestedCredentialData(new byte[]{2})
                .signCount(0)
                .displayName("패스키")
                .build();
        ReflectionTestUtils.setField(passkey, "id", 4L);
        given(userPasskeyRepository.findById(4L)).willReturn(Optional.of(passkey));

        passkeyService.delete(1L, 4L);
        verify(userPasskeyRepository).delete(passkey);
    }

    private static User sampleUser() {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
