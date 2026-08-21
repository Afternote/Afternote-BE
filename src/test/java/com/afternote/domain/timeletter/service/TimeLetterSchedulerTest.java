package com.afternote.domain.timeletter.service;

import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class TimeLetterSchedulerTest {

    @InjectMocks
    private TimeLetterScheduler timeLetterScheduler;

    @Mock
    private TimeLetterRepository timeLetterRepository;

    @Mock
    private TimeLetterDeliveryService timeLetterDeliveryService;

    @Test
    @DisplayName("DATE 타임레터만 날짜 기반 전달 서비스에 넘긴다")
    void updateScheduledToSentRecordsActualDeliveryTime() {
        TimeLetter dateLetter = TimeLetter.builder()
                .title("날짜 편지")
                .sendAt(LocalDateTime.now().minusMinutes(1))
                .status(TimeLetterStatus.SCHEDULED)
                .deliveryMode(TimeLetterDeliveryMode.DATE)
                .build();
        TimeLetter postDeathLetter = TimeLetter.builder()
                .title("사후 편지")
                .status(TimeLetterStatus.SCHEDULED)
                .deliveryMode(TimeLetterDeliveryMode.POST_DEATH)
                .build();
        given(timeLetterRepository.findByStatusAndSendAtBefore(
                eq(TimeLetterStatus.SCHEDULED),
                any(LocalDateTime.class)
        )).willReturn(List.of(dateLetter, postDeathLetter));

        timeLetterScheduler.updateScheduledToSent();

        verify(timeLetterDeliveryService).deliverDateLetters(
                eq(List.of(dateLetter)),
                any(LocalDateTime.class)
        );
        verifyNoMoreInteractions(timeLetterDeliveryService);
    }
}
