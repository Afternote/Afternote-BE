package com.afternote.domain.delivery.service;

import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.model.InactivityPeriod;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("미사용 전달 조건 스케줄러")
class DeliveryConditionSchedulerTest {

    @Mock
    private DeliveryConditionRepository deliveryConditionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReceiverRepository receiverRepository;

    @Mock
    private DeliveryNotificationService deliveryNotificationService;

    @InjectMocks
    private DeliveryConditionScheduler scheduler;

    @Test
    @DisplayName("유예 중 lastActiveAt 이 갱신되면 카카오 재로그인만으로 ACTIVE 복귀")
    void expireGracePeriod_LastActiveAfterGraceStart_ResetsToActive() {
        LocalDateTime graceStart = LocalDateTime.now().minusDays(3);
        DeliveryCondition condition = pendingInactivity(88L, graceStart);
        User user = kakaoUser(88L, graceStart.plusMinutes(1));

        given(deliveryConditionRepository.findByConditionTypeAndState(
                DeliveryConditionType.INACTIVITY, ConditionState.ACTIVE)).willReturn(List.of());
        given(deliveryConditionRepository.findByConditionTypeAndState(
                DeliveryConditionType.INACTIVITY, ConditionState.PENDING_CONFIRMATION))
                .willReturn(List.of(condition));
        given(userRepository.findAllById(any())).willReturn(List.of(user));

        scheduler.evaluateInactivityConditions();

        assertThat(condition.getState()).isEqualTo(ConditionState.ACTIVE);
        assertThat(condition.getGracePeriodStartedAt()).isNull();
        assertThat(condition.getFulfilledAt()).isNull();
        verify(deliveryNotificationService, never()).sendDeliveredToReceiver(any(), any(), any());
    }

    @Test
    @DisplayName("유예 중 lastActiveAt 이 그대로면 7일 뒤 FULFILLED")
    void expireGracePeriod_NoActivityDuringGrace_Fulfills() {
        LocalDateTime graceStart = LocalDateTime.now().minusDays(8);
        DeliveryCondition condition = pendingInactivity(88L, graceStart);
        User user = kakaoUser(88L, graceStart.minusDays(90));

        given(deliveryConditionRepository.findByConditionTypeAndState(
                DeliveryConditionType.INACTIVITY, ConditionState.ACTIVE)).willReturn(List.of());
        given(deliveryConditionRepository.findByConditionTypeAndState(
                DeliveryConditionType.INACTIVITY, ConditionState.PENDING_CONFIRMATION))
                .willReturn(List.of(condition));
        given(userRepository.findAllById(any())).willReturn(List.of(user));

        scheduler.evaluateInactivityConditions();

        assertThat(condition.getState()).isEqualTo(ConditionState.FULFILLED);
        assertThat(condition.getFulfilledAt()).isNotNull();
        verify(receiverRepository).findById(1L);
    }

    private static User kakaoUser(Long id, LocalDateTime lastActiveAt) {
        User user = User.builder()
                .email("kakao@test.com")
                .password(null)
                .name("kakao")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.KAKAO)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "lastActiveAt", lastActiveAt);
        return user;
    }

    private static DeliveryCondition pendingInactivity(Long userId, LocalDateTime graceStart) {
        DeliveryCondition condition = DeliveryCondition.builder()
                .userId(userId)
                .receiverId(1L)
                .contentType(DeliveryContentType.AFTERNOTE)
                .conditionType(DeliveryConditionType.INACTIVITY)
                .inactivityPeriod(InactivityPeriod.THREE_MONTHS)
                .build();
        condition.markPendingConfirmation(graceStart);
        return condition;
    }
}
