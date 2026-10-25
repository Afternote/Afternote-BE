package com.afternote.domain.notification.reminder;

import com.afternote.domain.notification.policy.*;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.timeletter.model.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TimeLetterReminderService {
    private final TimeLetterRepository letters;
    private final TimeLetterReceiverRepository receivers;
    private final UserRepository users;
    private final NotificationPolicyStore notifications;
    private final EntityManager entityManager;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void enqueue(Long letterId, TimeLetterReminderType type, LocalDateTime now) {
        TimeLetter letter = letters.findReminderCandidate(letterId).orElse(null);
        if (letter == null) return;
        var owner = users.findByIdForPushUpdate(letter.getUser().getId()).orElse(null);
        if (owner != null) entityManager.refresh(owner);
        if (owner == null || owner.getStatus() != UserStatus.ACTIVE || !owner.isTimeLetterPushEnabled()) return;
        // Another scheduler or a content edit may have committed while we waited for the owner lock.
        entityManager.refresh(letter);
        if (!isEligible(letter, type, now)) return;
        String receiverName = "수신자";
        if (type == TimeLetterReminderType.DAYS_30) {
            var targets = receivers.findByTimeLetterId(letterId);
            if (targets.isEmpty()) return;
            receiverName = targets.get(0).getReceiver().getName();
            if (targets.size() > 1) receiverName += " 외 " + (targets.size() - 1) + "명";
        }
        String key = type.eventType() + ":" + letterId + ":" + letter.getSendAt();
        notifications.enqueue(owner.getId(), key, NotificationCategory.E, NotificationEvent.SCHEDULED,
                type.title(), type.body(receiverName), type.data(letterId, letter.getSendAt()),
                letter.getSendAt().minusDays(type.days()));
    }

    static boolean isEligible(TimeLetter letter, TimeLetterReminderType type, LocalDateTime now) {
        if (letter.getStatus() != TimeLetterStatus.SCHEDULED || letter.isPostDeath() || letter.getSendAt() == null) return false;
        LocalDateTime due = letter.getSendAt().minusDays(type.days());
        return !now.isBefore(due) && now.isBefore(due.plusDays(1));
    }
}
