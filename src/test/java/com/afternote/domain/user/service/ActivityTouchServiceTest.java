package com.afternote.domain.user.service;

import com.afternote.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ActivityTouchServiceTest {

    @InjectMocks
    private ActivityTouchService activityTouchService;

    @Mock
    private UserRepository userRepository;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("touch — JVM 시간대와 관계없이 한국 시각으로 last_active_at 갱신")
    void touch_UpdatesLastActiveAt(String defaultZone) {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(defaultZone));
            var zone = java.time.ZoneId.of("Asia/Seoul");
            var before = LocalDateTime.now(zone);
            given(userRepository.updateLastActiveAt(eq(7L), any(LocalDateTime.class))).willReturn(1);
            activityTouchService.touch(7L);
            var after = LocalDateTime.now(zone);
            ArgumentCaptor<LocalDateTime> at = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userRepository).updateLastActiveAt(eq(7L), at.capture());
            assertThat(at.getValue()).isBetween(before, after);
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    @Test
    @DisplayName("touch — userId null 이면 repository 미호출")
    void touch_NullUserId_Skipped() {
        activityTouchService.touch(null);
        verify(userRepository, never()).updateLastActiveAt(any(), any());
    }

    @Test
    @DisplayName("touch — repository 예외여도 전파하지 않음")
    void touch_RepositoryError_Swallowed() {
        given(userRepository.updateLastActiveAt(eq(1L), any(LocalDateTime.class)))
                .willThrow(new RuntimeException("db down"));

        assertThatCode(() -> activityTouchService.touch(1L)).doesNotThrowAnyException();
    }
}
