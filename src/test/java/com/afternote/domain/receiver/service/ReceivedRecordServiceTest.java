package com.afternote.domain.receiver.service;

import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.ReceivedRecordBoxListResponse;
import com.afternote.domain.receiver.dto.ReceivedTimeLetterListResponse;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.*;
import com.afternote.domain.user.model.User;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceivedRecordServiceTest {

    private static final Long USER_ID = 20L;
    private static final Long SENDER_ID = 10L;
    private static final Long RECEIVER_ID = 30L;

    @InjectMocks
    private ReceivedRecordService receivedRecordService;

    @Mock private ReceiverRepository receiverRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReceivedService receivedService;
    @Mock private DeliveryVerificationService deliveryVerificationService;
    @Mock private S3Service s3Service;
    @Mock private DeliveryVerificationRepository deliveryVerificationRepository;
    @Mock private DeliveryConditionService deliveryConditionService;
    @Mock private TimeLetterReceiverRepository timeLetterReceiverRepository;
    @Mock private AfternoteReceiverRepository afternoteReceiverRepository;
    @Mock private DiaryReceiverRepository diaryReceiverRepository;
    @Mock private DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    @Mock private UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;

    @Test
    @DisplayName("받은 기록함 목록은 이메일이 아니라 로그인한 회원 ID로 조회한다")
    void getRecordBoxes_UsesAcceptedUserId() {
        given(receiverRepository.findAllByAcceptedUserIdOrderByIdDesc(USER_ID)).willReturn(List.of());

        ReceivedRecordBoxListResponse response = receivedRecordService.getRecordBoxes(USER_ID);

        assertThat(response.recordBoxes()).isEmpty();
        verify(receiverRepository).findAllByAcceptedUserIdOrderByIdDesc(USER_ID);
    }

    @Test
    @DisplayName("로그인한 회원이 수락한 수신자이면 받은 타임레터를 조회한다")
    void getTimeLetters_AcceptedReceiver_Success() {
        Receiver receiver = acceptedReceiver();
        ReceivedTimeLetterListResponse expected = ReceivedTimeLetterListResponse.from(List.of());
        given(receiverRepository.findByIdAndAcceptedUserId(RECEIVER_ID, USER_ID))
                .willReturn(Optional.of(receiver));
        given(receivedService.getTimeLetters(RECEIVER_ID)).willReturn(expected);

        assertThat(receivedRecordService.getTimeLetters(USER_ID, RECEIVER_ID)).isSameAs(expected);
    }

    @Test
    @DisplayName("다른 회원의 받은 기록함에는 접근할 수 없다")
    void getTimeLetters_OtherUser_Fail() {
        given(receiverRepository.findByIdAndAcceptedUserId(RECEIVER_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> receivedRecordService.getTimeLetters(USER_ID, RECEIVER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECEIVER_NOT_FOUND);
    }

    private Receiver acceptedReceiver() {
        User receiverUser = User.builder()
                .email("receiver@test.com")
                .name("수신자")
                .phone("010-1234-5678")
                .build();
        ReflectionTestUtils.setField(receiverUser, "id", USER_ID);
        Receiver receiver = Receiver.fromAcceptedInvitation(receiverUser, SENDER_ID);
        ReflectionTestUtils.setField(receiver, "id", RECEIVER_ID);
        return receiver;
    }
}
