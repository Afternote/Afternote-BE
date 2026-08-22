package com.afternote.domain.timeletter.service;

import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("타임레터 예약 전송 스케줄러")
class TimeLetterSchedulerTest {

    @Mock
    TimeLetterRepository timeLetterRepository;

    @InjectMocks
    TimeLetterScheduler scheduler;

    @Test
    @DisplayName("현재 시각까지 발송 예정인 타임레터를 한 번에 발송 완료 처리하도록 요청한다")
    void requestsOneScheduledDeliveryUpdateWithTheCurrentTime() {
        given(timeLetterRepository.markDueDateLettersAsSent(org.mockito.ArgumentMatchers.any()))
                .willReturn(3);
        ZoneId zone = ZoneId.of("Asia/Seoul");
        LocalDateTime before = LocalDateTime.now(zone);

        scheduler.updateScheduledToSent();

        LocalDateTime after = LocalDateTime.now(zone);
        ArgumentCaptor<LocalDateTime> processedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(timeLetterRepository).markDueDateLettersAsSent(processedAt.capture());
        assertThat(processedAt.getValue()).isBetween(before, after);
    }
}
