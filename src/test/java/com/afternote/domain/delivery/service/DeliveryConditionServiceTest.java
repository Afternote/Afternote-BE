package com.afternote.domain.delivery.service;

import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.timeletter.service.TimeLetterDeliveryService;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryConditionServiceTest {

    @InjectMocks
    private DeliveryConditionService deliveryConditionService;

    @Mock
    private DeliveryConditionRepository deliveryConditionRepository;
    @Mock
    private ReceiverRepository receiverRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DeliveryNotificationService deliveryNotificationService;
    @Mock
    private TimeLetterDeliveryService timeLetterDeliveryService;

    @Test
    @DisplayName("수신자 요청 조건 승인 처리 시각을 사후 타임레터 전달 시각으로 사용한다")
    void fulfillByReceiverRequestDeliversPostDeathTimeLetters() {
        DeliveryCondition condition = DeliveryCondition.builder()
                .userId(1L)
                .receiverId(2L)
                .contentType(DeliveryContentType.TIME_LETTER)
                .conditionType(DeliveryConditionType.RECEIVER_REQUEST)
                .build();
        given(deliveryConditionRepository.findByUserIdAndReceiverIdAndConditionType(
                1L,
                2L,
                DeliveryConditionType.RECEIVER_REQUEST
        )).willReturn(List.of(condition));

        deliveryConditionService.fulfillByReceiverRequest(1L, 2L);

        ArgumentCaptor<LocalDateTime> deliveredAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(timeLetterDeliveryService).deliverPostDeathLetters(eq(2L), deliveredAt.capture());
        assertThat(condition.isFulfilled()).isTrue();
        assertThat(condition.getFulfilledAt()).isEqualTo(deliveredAt.getValue());
    }
}
