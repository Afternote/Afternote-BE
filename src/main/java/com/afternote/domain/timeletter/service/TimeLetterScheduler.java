package com.afternote.domain.timeletter.service;

import com.afternote.domain.notification.reminder.TimeLetterDeliveryScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Date delivery is now evaluated per recipient by TimeLetterDeliveryScheduler. */
@Component
@RequiredArgsConstructor
public class TimeLetterScheduler {
    private final TimeLetterDeliveryScheduler delivery;
    public void updateScheduledToSent() { delivery.poll(); }
}
