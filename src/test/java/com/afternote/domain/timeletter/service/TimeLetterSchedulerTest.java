package com.afternote.domain.timeletter.service;

import com.afternote.domain.timeletter.repository.TimeLetterScheduledDeliveryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("타임레터 예약 전송 스케줄러")
class TimeLetterSchedulerTest {

    @Mock
    TimeLetterScheduledDeliveryRepository scheduledDeliveryRepository;

    @InjectMocks
    TimeLetterScheduler scheduler;

    @Test
    @DisplayName("현재 시각까지 발송 예정인 타임레터를 SENT 상태로 변경하도록 한 번 요청한다")
    void delegatesOneBulkTransitionWithTheCurrentCutoff() {
        given(scheduledDeliveryRepository.markDueDateLettersAsSent(org.mockito.ArgumentMatchers.any()))
                .willReturn(3);
        LocalDateTime before = LocalDateTime.now();

        scheduler.updateScheduledToSent();

        LocalDateTime after = LocalDateTime.now();
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(scheduledDeliveryRepository).markDueDateLettersAsSent(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, after);
    }
}
