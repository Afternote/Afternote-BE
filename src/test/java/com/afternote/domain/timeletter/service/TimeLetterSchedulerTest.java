package com.afternote.domain.timeletter.service;
import com.afternote.domain.notification.reminder.TimeLetterDeliveryScheduler;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
class TimeLetterSchedulerTest {
    @Test void delegatesToPerRecipientDeliveryInsteadOfBlindBulkSentUpdate() {
        var delivery = mock(TimeLetterDeliveryScheduler.class);
        new TimeLetterScheduler(delivery).updateScheduledToSent();
        verify(delivery).poll();
    }
}
