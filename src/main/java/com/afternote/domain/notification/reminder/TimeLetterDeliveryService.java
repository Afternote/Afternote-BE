package com.afternote.domain.notification.reminder;

import com.afternote.domain.notification.policy.*;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.timeletter.model.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class TimeLetterDeliveryService {
    private final EntityManager entityManager;
    private final TimeLetterRepository letters;
    private final TimeLetterReceiverRepository receivers;
    private final UserRepository users;
    private final NotificationPolicyStore notifications;
    private final Clock clock;
    public record Result(int delivered, int failed) { }
    public record RecipientState(Long receiverId, String name, String status,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) LocalDateTime deliveredAt) { }
    @Transactional(readOnly = true)
    public List<RecipientState> status(Long owner, Long id) {
        var letter = letters.findByIdAndUserId(id,owner).orElseThrow(() -> new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND));
        var at = LocalDateTime.now(clock.withZone(NotificationTiming.ZONE));
        return receivers.findByTimeLetterId(id).stream().map(t -> {
            boolean available = t.getReceiver().getAcceptedUserId() != null && users.findById(t.getReceiver().getAcceptedUserId())
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE).isPresent();
            boolean delivered = t.getDeliveredAt() != null && !t.getDeliveredAt().isAfter(at);
            String state = delivered ? "DELIVERED" : t.getReceiver().getAcceptedUserId() == null ? "UNACCEPTED"
                    : !available ? "ACCOUNT_INACTIVE" : "PENDING";
            return new RecipientState(t.getReceiver().getId(),t.getReceiver().getName(),state,delivered ? t.getDeliveredAt() : null);
        }).toList();
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result deliver(Long id, Long requestedOwner, UUID retryKey, LocalDateTime now) {
        var ref = letters.findReminderCandidate(id).orElse(null);
        if (ref == null) {
            if (requestedOwner != null) throw new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND);
            return new Result(0, 0);
        }
        Long ownerId = ref.getUser().getId();
        if (requestedOwner != null && !ownerId.equals(requestedOwner)) throw new CustomException(ErrorCode.TIME_LETTER_NOT_FOUND);
        var user = users.findByIdForPushUpdate(ownerId).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) return new Result(0, 0);
        var letter = letters.findByIdAndUserIdForUpdate(id, ownerId).orElseThrow(); entityManager.refresh(letter);
        if (letter.isPostDeath() || letter.getStatus() == TimeLetterStatus.DRAFT || letter.getSendAt() == null || letter.getSendAt().isAfter(now)) {
            if (requestedOwner != null) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            return new Result(0, 0);
        }
        String key = retryKey == null ? "DATE:" + id + ":" + letter.getSendAt() : "RETRY:" + ownerId + ":" + id + ":" + retryKey;
        var old = entityManager.find(LetterDeliveryRun.class, key);
        if (old != null) return new Result(old.getDeliveredCount(), old.getFailedCount());
        int delivered = 0; int failed = 0;
        var newRecipients = new ArrayList<String>();
        var targets = receivers.findByTimeLetterId(id);
        if (targets.isEmpty()) failed++;
        for (var target : targets) {
            var receiver = target.getReceiver();
            boolean available = receiver.getAcceptedUserId() != null
                    && users.findById(receiver.getAcceptedUserId()).filter(u -> u.getStatus() == UserStatus.ACTIVE).isPresent();
            // Legacy delivered_at may contain a planned future time. Only a committed successful run is trusted.
            boolean previouslyDelivered = !entityManager.createQuery("select r from LetterDeliveryRun r where r.letterId = :id and r.deliveredCount > 0", LetterDeliveryRun.class)
                    .setParameter("id", id).setMaxResults(1).getResultList().isEmpty() && target.getDeliveredAt() != null;
            if (previouslyDelivered) { delivered++; continue; }
            target.pendingDelivery();
            if (!available) { failed++; continue; }
            target.delivered(now); delivered++; newRecipients.add(receiver.getName());
            recipientDelivered(user, receiver, "LETTER:" + target.getId() + ":" + letter.getSendAt(), id, now);
        }
        if (delivered > 0) letter.markAsSent();
        entityManager.persist(new LetterDeliveryRun(key, id, now, delivered, failed));
        if (user.isTimeLetterPushEnabled()) {
            if (!newRecipients.isEmpty()) {
                String name = newRecipients.get(0) + (newRecipients.size() > 1 ? " 외 " + (newRecipients.size()-1) + "명" : "");
                notifications.enqueue(ownerId, key + ":SUCCESS", NotificationCategory.E, NotificationEvent.DELIVERY_COMPLETE,
                        "AfterNote", name + "님께 편지가 전달됐어요", Map.of("type", "TIME_LETTER_DELIVERED", "destination", "TIME_LETTER_DETAIL",
                                "timeLetterId", id.toString(), "action", "VIEW", "deliveredCount", Integer.toString(delivered), "failedCount", Integer.toString(failed)), now);
            }
            if (failed > 0) notifications.enqueue(ownerId, key + ":FAILURE", NotificationCategory.E, NotificationEvent.SEND_FAILURE,
                    "AfterNote", "편지를 전달하지 못했어요. 확인해주세요", Map.of("type", "TIME_LETTER_SEND_FAILED", "destination", "TIME_LETTER_DETAIL",
                            "timeLetterId", id.toString(), "action", "EDIT_CONTACT", "secondaryAction", "RETRY", "deliveredCount", Integer.toString(delivered), "failedCount", Integer.toString(failed)), now);
        }
        return new Result(delivered, failed);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reminder3(Long id, LocalDateTime now) {
        var letter = letters.findReminderCandidate(id).orElse(null);
        if (letter == null) return;
        var user = users.findByIdForPushUpdate(letter.getUser().getId()).orElse(null); entityManager.refresh(letter);
        if (user == null || user.getStatus() != UserStatus.ACTIVE || !user.isTimeLetterPushEnabled()
                || letter.isPostDeath() || letter.getStatus() != TimeLetterStatus.SCHEDULED || letter.getSendAt() == null) return;
        var due = letter.getSendAt().minusDays(3);
        if (now.isBefore(due) || !now.isBefore(due.plusDays(1))) return;
        for (var target : receivers.findByTimeLetterId(id)) {
            if (target.getReceiver().getAcceptedUserId() != null) continue;
            var receiver = target.getReceiver();
            notifications.enqueue(user.getId(), "LETTER3:" + id + ":" + receiver.getId() + ":" + letter.getSendAt(),
                    NotificationCategory.E, NotificationEvent.SCHEDULED, "AfterNote", receiver.getName() + "님이 아직 수락하지 않아서 전달할 수 없어요",
                    Map.of("type", "TIME_LETTER_UNACCEPTED_3D", "destination", "RECEIVER_DETAIL", "receiverId", receiver.getId().toString(),
                            "timeLetterId", id.toString(), "scheduledSendAt", letter.getSendAt().toString(), "action", "REINVITE"), due);
        }
    }
    private void recipientDelivered(User owner, Receiver receiver, String event, Long letterId, LocalDateTime at) {
        var data = new HashMap<String, String>(); data.put("type", "CONTENT_DELIVERED"); data.put("destination", "RECEIVER_HOME");
        data.put("receiverId", receiver.getId().toString()); if (letterId != null) data.put("timeLetterId", letterId.toString());
        if (event.startsWith("CONDITION:")) {
            String rest = event.substring("CONDITION:".length()); int delimiter = rest.indexOf(':');
            data.put("conditionId", rest.substring(0, delimiter)); data.put("fulfilledAt", rest.substring(delimiter + 1));
        }
        notifications.enqueueWithChannels(owner.getId(), "DELIVERED:" + event, NotificationCategory.D, NotificationEvent.DELIVERY_COMPLETE,
                owner.getName() + "님이 남기신 기록이 도착했어요", data, at, NotificationChannel.ALIMTALK, receiver.getPhone(), receiver.getAcceptedUserId(), true);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void fulfilledCondition(Long conditionId, LocalDateTime now) {
        var ref = entityManager.find(com.afternote.domain.delivery.model.DeliveryCondition.class, conditionId);
        if (ref == null) return;
        var owner = users.findByIdForPushUpdate(ref.getUserId()).orElse(null); entityManager.refresh(ref);
        if (owner == null || owner.getStatus() != UserStatus.ACTIVE || ref.getState() != com.afternote.domain.delivery.model.ConditionState.FULFILLED) return;
        var receiver = entityManager.find(Receiver.class, ref.getReceiverId());
        if (receiver == null || receiver.getAcceptedUserId() == null || users.findById(receiver.getAcceptedUserId()).filter(u -> u.getStatus() == UserStatus.ACTIVE).isEmpty()) return;
        boolean hasContent = false;
        if (ref.getContentType() == com.afternote.domain.delivery.model.DeliveryContentType.TIME_LETTER) {
            var rows = entityManager.createQuery("select t from TimeLetterReceiver t join fetch t.timeLetter l where t.receiver.id = :receiver and l.deliveryMode = com.afternote.domain.timeletter.model.TimeLetterDeliveryMode.POST_DEATH and l.status <> com.afternote.domain.timeletter.model.TimeLetterStatus.DRAFT", TimeLetterReceiver.class)
                    .setParameter("receiver", receiver.getId()).getResultList();
            for (var target : rows) {
                target.delivered(ref.getFulfilledAt()); hasContent = true;
                if (owner.isTimeLetterPushEnabled()) notifications.enqueue(owner.getId(), "POST_DEATH:" + conditionId + ":" + ref.getFulfilledAt() + ":" + target.getId(),
                        NotificationCategory.E, NotificationEvent.DELIVERY_COMPLETE, "AfterNote", receiver.getName() + "님께 편지가 전달됐어요",
                        Map.of("type", "TIME_LETTER_DELIVERED", "destination", "TIME_LETTER_DETAIL", "timeLetterId", target.getTimeLetter().getId().toString(), "action", "VIEW"), now);
            }
        } else if (ref.getContentType() == com.afternote.domain.delivery.model.DeliveryContentType.AFTERNOTE) {
            hasContent = entityManager.createQuery("select count(r) from AfternoteReceiver r where r.receiver.id = :receiver", Long.class).setParameter("receiver", receiver.getId()).getSingleResult() > 0;
        } else {
            String entity = switch (ref.getContentType()) {
                case DIARY -> "DiaryReceiver"; case DEEP_THOUGHT -> "DeepThoughtReceiver"; case DAILY_QUESTION -> "UserDailyQuestionReceiver";
                default -> throw new IllegalStateException("Unexpected content type");
            };
            String source = entity.equals("DiaryReceiver") ? "diary" : entity.equals("DeepThoughtReceiver") ? "deepThought" : "userDailyQuestion";
            hasContent = entityManager.createQuery("select count(r) from " + entity + " r where r.receiver.id = :receiver and r." + source + ".isDraft = false", Long.class)
                    .setParameter("receiver", receiver.getId()).getSingleResult() > 0;
        }
        if (hasContent) recipientDelivered(owner, receiver, "CONDITION:" + conditionId + ":" + ref.getFulfilledAt(), null, now);
    }
}
