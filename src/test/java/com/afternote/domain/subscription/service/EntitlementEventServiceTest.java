package com.afternote.domain.subscription.service;

import com.afternote.domain.subscription.model.EntitlementEventType;
import com.afternote.domain.subscription.model.EntitlementFeature;
import com.afternote.domain.subscription.model.EntitlementProvider;
import com.afternote.domain.subscription.repository.EntitlementEventStore;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntitlementEventServiceTest {

    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 8, 22, 18, 0);

    @Mock private EntitlementEventStore entitlementEventStore;
    @Mock private UserRepository userRepository;

    private EntitlementEventService entitlementEventService;

    @BeforeEach
    void setUp() {
        entitlementEventService = new EntitlementEventService(
                entitlementEventStore,
                userRepository,
                Clock.fixed(Instant.parse("2026-08-22T09:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("이벤트 처리 이력 추가 결과가 신규이면 true를 반환하고 현재 이용 권한 갱신을 요청한다")
    void applyVerifiedEvent_unprocessedEvent() {
        EntitlementEventCommand command = command("event-1");
        given(userRepository.existsById(1L)).willReturn(true);
        given(entitlementEventStore.appendIfAbsent(command, RECORDED_AT)).willReturn(true);

        boolean applied = entitlementEventService.applyVerifiedEvent(command);

        assertThat(applied).isTrue();
        verify(entitlementEventStore).upsertSnapshot(command, RECORDED_AT);
    }

    @Test
    @DisplayName("이벤트 처리 이력 추가 결과가 중복이면 false를 반환하고 현재 이용 권한 갱신을 요청하지 않는다")
    void applyVerifiedEvent_duplicateEvent() {
        EntitlementEventCommand command = command("event-1");
        given(userRepository.existsById(1L)).willReturn(true);
        given(entitlementEventStore.appendIfAbsent(command, RECORDED_AT)).willReturn(false);

        boolean applied = entitlementEventService.applyVerifiedEvent(command);

        assertThat(applied).isFalse();
        verify(entitlementEventStore, never()).upsertSnapshot(command, RECORDED_AT);
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 USER_NOT_FOUND를 던지고 이벤트 저장소를 호출하지 않는다")
    void applyVerifiedEvent_unknownUser() {
        EntitlementEventCommand command = command("event-1");
        given(userRepository.existsById(1L)).willReturn(false);

        assertThatThrownBy(() -> entitlementEventService.applyVerifiedEvent(command))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(entitlementEventStore);
    }

    private EntitlementEventCommand command(String eventId) {
        return new EntitlementEventCommand(
                EntitlementProvider.GOOGLE_PLAY,
                eventId,
                1L,
                EntitlementFeature.TIME_LETTER_UNLIMITED,
                EntitlementEventType.RENEWED,
                "purchase-token",
                LocalDateTime.of(2026, 8, 22, 17, 59),
                LocalDateTime.of(2026, 8, 22, 0, 0),
                LocalDateTime.of(2026, 9, 22, 0, 0)
        );
    }
}
