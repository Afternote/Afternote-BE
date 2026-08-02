package com.afternote.domain.user.service;

import com.afternote.domain.auth.service.TokenService;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.receiver.service.AuthCodeMessageService;
import com.afternote.domain.receiver.service.DeliveryVerificationService;
import com.afternote.domain.user.dto.UserCreateReceiverRequest;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserProviderRepository;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceCreateReceiverTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserReceiverRepository userReceiverRepository;
    @Mock
    private ReceiverRepository receiverRepository;
    @Mock
    private AuthCodeMessageService authCodeMessageService;
    @Mock
    private S3Service s3Service;
    @Mock
    private DeliveryVerificationService deliveryVerificationService;
    @Mock
    private TokenService tokenService;
    @Mock
    private SocialLoginFactory socialLoginFactory;
    @Mock
    private UserProviderRepository userProviderRepository;

    @Test
    @DisplayName("수신자 등록 실패 - 전화번호 형식")
    void createReceiver_InvalidPhone_Fail() {
        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        UserCreateReceiverRequest request = new UserCreateReceiverRequest(
                "Invalid Phone", "아들", "abc123", "a@a.com", null
        );

        assertThatThrownBy(() -> userService.createReceiver(1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_PHONE_FORMAT));
        verify(receiverRepository, never()).save(any());
    }

    @Test
    @DisplayName("수신자 등록 실패 - 동일 전화번호 중복")
    void createReceiver_DuplicatePhone_Fail() {
        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        Receiver existing = Receiver.builder()
                .name("기존")
                .relation("아들")
                .phone("010-1234-5678")
                .userId(1L)
                .build();
        ReflectionTestUtils.setField(existing, "id", 12L);
        given(receiverRepository.findAllByUserId(1L)).willReturn(List.of(existing));

        UserCreateReceiverRequest request = new UserCreateReceiverRequest(
                "Invalid Phone Dup", "아들", "01012345678", "b@b.com", null
        );

        assertThatThrownBy(() -> userService.createReceiver(1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_RECEIVER_PHONE));
        verify(receiverRepository, never()).save(any());
    }

    @Test
    @DisplayName("수신자 등록 성공 - 유효 전화번호")
    void createReceiver_ValidPhone_Success() {
        User user = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(receiverRepository.findAllByUserId(1L)).willReturn(List.of());
        given(receiverRepository.save(any(Receiver.class))).willAnswer(invocation -> {
            Receiver saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            return saved;
        });
        given(userReceiverRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        UserCreateReceiverRequest request = new UserCreateReceiverRequest(
                "김지은", "딸", "010-1234-5678", "jieun@naver.com", null
        );

        var response = userService.createReceiver(1L, request);

        assertThat(response.receiverId()).isEqualTo(100L);
        verify(receiverRepository).save(any(Receiver.class));
    }

    private static User sampleUser(Long id) {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
