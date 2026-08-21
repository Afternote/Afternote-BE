package com.afternote.domain.delivery.service;

import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.model.InactivityPeriod;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.timeletter.service.TimeLetterDeliveryService;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryConditionSchedulerTest {

    @InjectMocks
    private DeliveryConditionScheduler deliveryConditionScheduler;

    @Mock
    private DeliveryConditionRepository deliveryConditionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReceiverRepository receiverRepository;
    @Mock
    private DeliveryNotificationService deliveryNotificationService;
    @Mock
    private TimeLetterDeliveryService timeLetterDeliveryService;
    @Mock
    private EntityManager entityManager;

    @Test
    @DisplayName("Receiver 잠금 후 미사용 유예 만료와 사후 타임레터 전달을 처리한다")
    void expireGracePeriodDeliversPostDeathTimeLetters() {
        LocalDateTime graceStartedAt = LocalDateTime.now().minusDays(8);
        DeliveryCondition condition = DeliveryCondition.builder()
                .userId(1L)
                .receiverId(2L)
                .contentType(DeliveryContentType.TIME_LETTER)
                .conditionType(DeliveryConditionType.INACTIVITY)
                .inactivityPeriod(InactivityPeriod.THREE_MONTHS)
                .build();
        condition.markPendingConfirmation(graceStartedAt);

        User user = User.builder()
                .email("sender@example.com")
                .password("password123!")
                .name("발신자")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(user, "lastActiveAt", graceStartedAt.minusDays(1));

        Receiver receiver = Receiver.builder()
                .userId(1L)
                .name("수신자")
                .email("receiver@example.com")
                .build();
        ReflectionTestUtils.setField(receiver, "id", 2L);

        given(deliveryConditionRepository.findByConditionTypeAndState(
                DeliveryConditionType.INACTIVITY,
                ConditionState.ACTIVE
        )).willReturn(List.of());
        given(deliveryConditionRepository.findByConditionTypeAndState(
                DeliveryConditionType.INACTIVITY,
                ConditionState.PENDING_CONFIRMATION
        )).willReturn(List.of(condition));
        given(userRepository.findAllById(Set.of(1L))).willReturn(List.of(user));
        given(receiverRepository.findAllByIdInOrderByIdForUpdate(List.of(2L)))
                .willReturn(List.of(receiver));

        deliveryConditionScheduler.evaluateInactivityConditions();

        ArgumentCaptor<LocalDateTime> deliveredAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(timeLetterDeliveryService).deliverPostDeathLetters(eq(2L), deliveredAt.capture());
        assertThat(condition.isFulfilled()).isTrue();
        assertThat(condition.getFulfilledAt()).isEqualTo(deliveredAt.getValue());

        var order = inOrder(receiverRepository, entityManager, timeLetterDeliveryService);
        order.verify(receiverRepository).findAllByIdInOrderByIdForUpdate(List.of(2L));
        order.verify(entityManager).refresh(condition, LockModeType.PESSIMISTIC_WRITE);
        order.verify(timeLetterDeliveryService).deliverPostDeathLetters(eq(2L), eq(deliveredAt.getValue()));
    }
}
