package com.afternote.domain.notification.reminder;

import com.afternote.domain.notification.policy.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeLetterReminderGuard implements NotificationDispatchGuard {
    private final TimeLetterRepository letters;
    private final ObjectMapper objectMapper;

    @Override
    public boolean allows(NotificationDispatch notification, LocalDateTime now) {
        try {
            Map<String, String> data = objectMapper.readValue(notification.getPayloadJson(), new TypeReference<>() { });
            TimeLetterReminderType type = TimeLetterReminderType.fromEventType(data.get("type"));
            if (type == null) return true;
            Long id = Long.valueOf(data.get("timeLetterId"));
            LocalDateTime expectedSendAt = LocalDateTime.parse(data.get("scheduledSendAt"));
            var letter = letters.findReminderCandidate(id).orElse(null);
            return letter != null && letter.getUser().getId().equals(notification.getUserId())
                    && letter.getUser().isTimeLetterPushEnabled()
                    && expectedSendAt.equals(letter.getSendAt())
                    && TimeLetterReminderService.isEligible(letter, type, now);
        } catch (java.io.IOException | IllegalArgumentException | java.time.DateTimeException | NullPointerException e) {
            return false;
        }
    }
}
