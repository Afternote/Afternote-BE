package com.afternote.domain.notification.reminder;

import com.afternote.domain.notification.policy.NotificationTiming;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TimeLetterReminderScheduler {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.afternote.domain.notification.policy.NotificationReadiness readiness;

    private final TimeLetterRepository letters;
    private final TimeLetterReminderService reminders;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${notification.time-letter-reminder.poll-ms:60000}")
    public void poll() {
        if (readiness != null && !readiness.isReady()) return;
        LocalDateTime now = LocalDateTime.now(clock.withZone(NotificationTiming.ZONE));
        for (TimeLetterReminderType type : TimeLetterReminderType.values()) {
            long afterId = 0;
            while (true) {
                var ids = letters.findReminderCandidateIds(now.plusDays(type.days()).minusDays(1),
                        now.plusDays(type.days()), afterId, PageRequest.of(0, 200));
                if (ids.isEmpty()) break;
                for (Long id : ids) {
                    try { reminders.enqueue(id, type, now); }
                    catch (RuntimeException e) { log.warn("Time letter reminder enqueue failed. letterId={}, type={}", id, type, e); }
                }
                afterId = ids.get(ids.size() - 1);
            }
        }
    }
}
