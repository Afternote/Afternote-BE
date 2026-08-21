package com.afternote.domain.timeletter.service;

import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class TimeLetterDeliveryServiceTest {

    @InjectMocks
    private TimeLetterDeliveryService timeLetterDeliveryService;

    @Mock
    private TimeLetterReceiverRepository timeLetterReceiverRepository;

    @Mock
    private DeliveryConditionRepository deliveryConditionRepository;

    private User user;
    private Receiver receiver;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("sender@example.com")
                .password("password123!")
                .name("발신자")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        receiver = Receiver.builder()
                .userId(1L)
                .name("수신자")
                .email("receiver@example.com")
                .build();
        ReflectionTestUtils.setField(receiver, "id", 2L);
    }

    @Test
    @DisplayName("날짜 발송 시 타임레터 상태와 모든 수신자 연결의 전달 시각을 갱신한다")
    void deliverDateLettersMarksActualDeliveryTime() {
        TimeLetter timeLetter = timeLetter(TimeLetterDeliveryMode.DATE);
        TimeLetterReceiver timeLetterReceiver = timeLetterReceiver(timeLetter);
        LocalDateTime deliveredAt = LocalDateTime.of(2026, 8, 21, 3, 0);

        given(timeLetterReceiverRepository.findByTimeLetterIdIn(List.of(10L)))
                .willReturn(List.of(timeLetterReceiver));

        timeLetterDeliveryService.deliverDateLetters(List.of(timeLetter), deliveredAt);

        assertThat(timeLetter.getStatus()).isEqualTo(TimeLetterStatus.SENT);
        assertThat(timeLetterReceiver.getDeliveredAt()).isEqualTo(deliveredAt);
    }

    @Test
    @DisplayName("사후 조건 충족 시 해당 수신자의 타임레터만 실제 전달 처리한다")
    void deliverPostDeathLettersMarksReceiverDeliveryTime() {
        TimeLetter timeLetter = timeLetter(TimeLetterDeliveryMode.POST_DEATH);
        TimeLetterReceiver timeLetterReceiver = timeLetterReceiver(timeLetter);
        LocalDateTime deliveredAt = LocalDateTime.of(2026, 8, 21, 3, 10);

        given(timeLetterReceiverRepository.findUndeliveredPostDeathByReceiverId(
                2L,
                TimeLetterDeliveryMode.POST_DEATH,
                TimeLetterStatus.DRAFT
        )).willReturn(List.of(timeLetterReceiver));

        int deliveredCount = timeLetterDeliveryService.deliverPostDeathLetters(2L, deliveredAt);

        assertThat(deliveredCount).isEqualTo(1);
        assertThat(timeLetterReceiver.getDeliveredAt()).isEqualTo(deliveredAt);
        assertThat(timeLetter.getStatus()).isEqualTo(TimeLetterStatus.SENT);
    }

    @Test
    @DisplayName("사후 조건이 먼저 충족됐으면 정식 등록 시점에 즉시 전달한다")
    void deliverPostDeathWhenConditionWasAlreadyFulfilled() {
        TimeLetter timeLetter = timeLetter(TimeLetterDeliveryMode.POST_DEATH);
        TimeLetterReceiver timeLetterReceiver = timeLetterReceiver(timeLetter);
        DeliveryCondition condition = DeliveryCondition.builder()
                .userId(1L)
                .receiverId(2L)
                .contentType(DeliveryContentType.TIME_LETTER)
                .conditionType(DeliveryConditionType.RECEIVER_REQUEST)
                .build();
        condition.fulfill(LocalDateTime.of(2026, 8, 20, 12, 0));
        LocalDateTime deliveredAt = LocalDateTime.of(2026, 8, 21, 3, 20);

        given(timeLetterReceiverRepository.findByTimeLetterIdForUpdate(10L))
                .willReturn(List.of(timeLetterReceiver));
        given(deliveryConditionRepository.findByReceiverIdInAndContentTypeAndStateForUpdate(
                List.of(2L),
                DeliveryContentType.TIME_LETTER,
                ConditionState.FULFILLED
        )).willReturn(List.of(condition));

        int deliveredCount = timeLetterDeliveryService
                .deliverPostDeathIfConditionAlreadyFulfilled(timeLetter, deliveredAt);

        assertThat(deliveredCount).isEqualTo(1);
        assertThat(timeLetterReceiver.getDeliveredAt()).isEqualTo(deliveredAt);
    }

    @Test
    @DisplayName("이미 기록된 실제 전달 시각은 다시 호출해도 덮어쓰지 않는다")
    void deliveryTimeIsIdempotent() {
        TimeLetter timeLetter = timeLetter(TimeLetterDeliveryMode.POST_DEATH);
        TimeLetterReceiver timeLetterReceiver = timeLetterReceiver(timeLetter);
        LocalDateTime firstDelivery = LocalDateTime.of(2026, 8, 21, 3, 30);
        LocalDateTime retry = firstDelivery.plusMinutes(1);

        given(timeLetterReceiverRepository.findUndeliveredPostDeathByReceiverId(
                2L,
                TimeLetterDeliveryMode.POST_DEATH,
                TimeLetterStatus.DRAFT
        )).willReturn(List.of(timeLetterReceiver));

        timeLetterDeliveryService.deliverPostDeathLetters(2L, firstDelivery);
        timeLetterDeliveryService.deliverPostDeathLetters(2L, retry);

        assertThat(timeLetterReceiver.getDeliveredAt()).isEqualTo(firstDelivery);
    }

    private TimeLetter timeLetter(TimeLetterDeliveryMode deliveryMode) {
        TimeLetter timeLetter = TimeLetter.builder()
                .user(user)
                .title("타임레터")
                .sendAt(deliveryMode == TimeLetterDeliveryMode.DATE
                        ? LocalDateTime.of(2026, 8, 21, 2, 0)
                        : null)
                .status(TimeLetterStatus.SCHEDULED)
                .deliveryMode(deliveryMode)
                .build();
        ReflectionTestUtils.setField(timeLetter, "id", 10L);
        return timeLetter;
    }

    private TimeLetterReceiver timeLetterReceiver(TimeLetter timeLetter) {
        return TimeLetterReceiver.builder()
                .timeLetter(timeLetter)
                .receiver(receiver)
                .build();
    }
}
