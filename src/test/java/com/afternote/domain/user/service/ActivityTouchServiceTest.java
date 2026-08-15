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

    @Test
    @DisplayName("touch — last_active_at 벌크 갱신")
    void touch_UpdatesLastActiveAt() {
        given(userRepository.updateLastActiveAt(eq(7L), any(LocalDateTime.class))).willReturn(1);

        activityTouchService.touch(7L);

        ArgumentCaptor<LocalDateTime> at = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(userRepository).updateLastActiveAt(eq(7L), at.capture());
        assertThat(at.getValue()).isBeforeOrEqualTo(LocalDateTime.now().plusSeconds(1));
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
