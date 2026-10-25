package com.afternote.domain.notification.reminder;

import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.notification.policy.NotificationTiming;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TimeLetterReminderSchedulerTest {
    @Test void keysetScanAdvancesPastFirstBatch() {
        var letters=mock(TimeLetterRepository.class);
        var reminders=mock(TimeLetterReminderService.class);
        LocalDateTime now=LocalDateTime.of(2026,10,17,12,0);
        Clock clock=Clock.fixed(now.atZone(NotificationTiming.ZONE).toInstant(), NotificationTiming.ZONE);
        when(letters.findReminderCandidateIds(eq(now.plusDays(29)), eq(now.plusDays(30)), eq(0L), any(Pageable.class)))
                .thenReturn(List.of());
        when(letters.findReminderCandidateIds(eq(now.plusDays(6)), eq(now.plusDays(7)), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(10L,20L));
        when(letters.findReminderCandidateIds(eq(now.plusDays(6)), eq(now.plusDays(7)), eq(20L), any(Pageable.class)))
                .thenReturn(List.of(30L));
        when(letters.findReminderCandidateIds(eq(now.plusDays(6)), eq(now.plusDays(7)), eq(30L), any(Pageable.class)))
                .thenReturn(List.of());
        new TimeLetterReminderScheduler(letters,reminders,clock).poll();
        verify(reminders).enqueue(10L,TimeLetterReminderType.DAYS_7,now);
        verify(reminders).enqueue(20L,TimeLetterReminderType.DAYS_7,now);
        verify(reminders).enqueue(30L,TimeLetterReminderType.DAYS_7,now);
        verifyNoMoreInteractions(reminders);
    }
}
