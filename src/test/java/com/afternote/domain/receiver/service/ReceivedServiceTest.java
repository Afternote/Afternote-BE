package com.afternote.domain.receiver.service;

import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.receiver.repository.*;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.timeletter.service.TimeLetterDeliveryService;
import com.afternote.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReceivedServiceTest {

    @InjectMocks
    private ReceivedService receivedService;

    @Mock private ReceiverRepository receiverRepository;
    @Mock private TimeLetterReceiverRepository timeLetterReceiverRepository;
    @Mock private AfternoteReceiverRepository afternoteReceiverRepository;
    @Mock private DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    @Mock private DiaryReceiverRepository diaryReceiverRepository;
    @Mock private UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;
    @Mock private TimeLetterRepository timeLetterRepository;
    @Mock private DeepThoughtRepository deepThoughtRepository;
    @Mock private DiaryRepository diaryRepository;
    @Mock private UserDailyQuestionRepository userDailyQuestionRepository;
    @Mock private UserRepository userRepository;
    @Mock private S3Service s3Service;
    @Mock private MindRecordReceiverService mindRecordReceiverService;
    @Mock private DeliveryConditionService deliveryConditionService;
    @Mock private TimeLetterDeliveryService timeLetterDeliveryService;

    @Test
    @DisplayName("타임레터 수신자 연결은 Receiver를 ID 순으로 잠근 뒤 저장한다")
    void createTimeLetterReceiversLocksReceiversBeforeSavingLinks() {
        Receiver receiver2 = receiver(2L);
        Receiver receiver3 = receiver(3L);
        TimeLetter timeLetter = postDeathTimeLetter(10L);

        given(receiverRepository.findAllByIdInOrderByIdForUpdate(List.of(2L, 3L)))
                .willReturn(List.of(receiver2, receiver3));
        given(timeLetterReceiverRepository.saveAll(anyList()))
                .willAnswer(invocation -> invocation.getArgument(0));

        receivedService.createTimeLetterReceivers(timeLetter, 1L, List.of(3L, 2L, 3L));

        InOrder order = inOrder(receiverRepository, timeLetterReceiverRepository);
        order.verify(receiverRepository).findAllByIdInOrderByIdForUpdate(List.of(2L, 3L));
        order.verify(timeLetterReceiverRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("POST_DEATH 전달 불변식 위반을 상세 조회에서 복구하지 않는다")
    void getTimeLetterDoesNotRepairUndeliveredPostDeathLink() {
        Receiver receiver = receiver(2L);
        TimeLetterReceiver link = TimeLetterReceiver.builder()
                .timeLetter(postDeathTimeLetter(10L))
                .receiver(receiver)
                .build();
        ReflectionTestUtils.setField(link, "id", 20L);
        given(timeLetterReceiverRepository.findByIdAndReceiverIdWithTimeLetter(20L, 2L))
                .willReturn(Optional.of(link));

        assertThatThrownBy(() -> receivedService.getTimeLetter(2L, 20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("timeLetterReceiverId=20");
        verifyNoInteractions(timeLetterDeliveryService);
    }

    private Receiver receiver(Long id) {
        Receiver receiver = Receiver.builder()
                .userId(1L)
                .name("수신자")
                .email("receiver" + id + "@example.com")
                .build();
        ReflectionTestUtils.setField(receiver, "id", id);
        return receiver;
    }

    private TimeLetter postDeathTimeLetter(Long id) {
        TimeLetter timeLetter = TimeLetter.builder()
                .title("사후 타임레터")
                .status(TimeLetterStatus.SCHEDULED)
                .deliveryMode(TimeLetterDeliveryMode.POST_DEATH)
                .build();
        ReflectionTestUtils.setField(timeLetter, "id", id);
        return timeLetter;
    }
}
