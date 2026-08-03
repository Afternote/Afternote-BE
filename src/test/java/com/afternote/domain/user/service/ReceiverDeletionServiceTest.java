package com.afternote.domain.user.service;

import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.UserReceiver;
import com.afternote.domain.receiver.repository.AfternoteReceiverRepository;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.repository.DeliveryVerificationRepository;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceiverDeletionServiceTest {

    @InjectMocks
    private ReceiverDeletionService receiverDeletionService;

    @Mock private UserRepository userRepository;
    @Mock private UserReceiverRepository userReceiverRepository;
    @Mock private ReceiverRepository receiverRepository;
    @Mock private TimeLetterReceiverRepository timeLetterReceiverRepository;
    @Mock private AfternoteReceiverRepository afternoteReceiverRepository;
    @Mock private DiaryReceiverRepository diaryReceiverRepository;
    @Mock private DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    @Mock private UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;
    @Mock private DeliveryConditionRepository deliveryConditionRepository;
    @Mock private DeliveryVerificationRepository deliveryVerificationRepository;

    @Test
    @DisplayName("콘텐츠에 연결된 수신자 삭제 거부")
    void deleteReceiver_LinkedToContent_Fail() {
        User user = sampleUser(1L);
        Receiver receiver = sampleReceiver(17L, 1L);
        UserReceiver link = UserReceiver.builder().user(user).receiver(receiver).build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userReceiverRepository.findByUserAndReceiverId(user, 17L)).willReturn(Optional.of(link));
        given(timeLetterReceiverRepository.existsByReceiverId(17L)).willReturn(true);

        assertThatThrownBy(() -> receiverDeletionService.deleteReceiver(1L, 17L))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.RECEIVER_IN_USE));

        verify(receiverRepository, never()).delete(receiver);
    }

    @Test
    @DisplayName("연결 없는 수신자 hard delete")
    void deleteReceiver_Unlinked_Success() {
        User user = sampleUser(1L);
        Receiver receiver = sampleReceiver(17L, 1L);
        UserReceiver link = UserReceiver.builder().user(user).receiver(receiver).build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userReceiverRepository.findByUserAndReceiverId(user, 17L)).willReturn(Optional.of(link));
        given(timeLetterReceiverRepository.existsByReceiverId(17L)).willReturn(false);
        given(afternoteReceiverRepository.existsByReceiverId(17L)).willReturn(false);
        given(diaryReceiverRepository.existsByReceiverId(17L)).willReturn(false);
        given(deepThoughtReceiverRepository.existsByReceiverId(17L)).willReturn(false);
        given(userDailyQuestionReceiverRepository.existsByReceiverId(17L)).willReturn(false);

        receiverDeletionService.deleteReceiver(1L, 17L);

        verify(deliveryConditionRepository).deleteByReceiverId(17L);
        verify(deliveryVerificationRepository).deleteByReceiverId(17L);
        verify(userReceiverRepository).delete(link);
        verify(receiverRepository).delete(receiver);
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

    private static Receiver sampleReceiver(Long id, Long userId) {
        Receiver receiver = Receiver.builder()
                .name("kim")
                .relation("아들")
                .userId(userId)
                .build();
        ReflectionTestUtils.setField(receiver, "id", id);
        return receiver;
    }
}
