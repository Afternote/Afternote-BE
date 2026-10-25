package com.afternote.domain.notification.reminder;
import com.afternote.domain.notification.policy.*;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.receiver.model.Receiver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component
@RequiredArgsConstructor
public class TimeLetterDeliveryGuard implements NotificationDispatchGuard {
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    public boolean allows(NotificationDispatch n, LocalDateTime now) {
        try {
            Map<String, String> data = mapper.readValue(n.getPayloadJson(), new TypeReference<>() { });
            String type = data.getOrDefault("type", "");
            if (!java.util.Set.of("TIME_LETTER_UNACCEPTED_3D", "TIME_LETTER_DELIVERED", "TIME_LETTER_SEND_FAILED", "CONTENT_DELIVERED").contains(type)) return true;
            if (data.containsKey("timeLetterId")) {
                var letter = entityManager.find(TimeLetter.class, Long.valueOf(data.get("timeLetterId")));
                if (letter == null || !letter.getUser().getId().equals(n.getUserId())) return false;
                if (!type.equals("CONTENT_DELIVERED") && !letter.getUser().isTimeLetterPushEnabled()) return false;
                if (type.equals("TIME_LETTER_UNACCEPTED_3D")) {
                    var receiver = entityManager.find(Receiver.class, Long.valueOf(data.get("receiverId")));
                    var due = letter.getSendAt() == null ? null : letter.getSendAt().minusDays(3);
                    return receiver != null && receiver.getUserId().equals(n.getUserId()) && receiver.getAcceptedUserId() == null
                            && letter.getStatus() == com.afternote.domain.timeletter.model.TimeLetterStatus.SCHEDULED && !letter.isPostDeath()
                            && letter.getSendAt().toString().equals(data.get("scheduledSendAt")) && due != null
                            && !now.isBefore(due) && now.isBefore(due.plusDays(1));
                }
            }
            if (type.equals("CONTENT_DELIVERED")) {
                if (data.containsKey("conditionId")) {
                    var condition = entityManager.find(com.afternote.domain.delivery.model.DeliveryCondition.class, Long.valueOf(data.get("conditionId")));
                    if (condition == null || condition.getState() != com.afternote.domain.delivery.model.ConditionState.FULFILLED
                            || !java.util.Objects.toString(condition.getFulfilledAt(), "").equals(data.get("fulfilledAt"))) return false;
                }
                var receiver = entityManager.find(Receiver.class, Long.valueOf(data.get("receiverId")));
                return receiver != null && receiver.getUserId().equals(n.getUserId())
                        && java.util.Objects.equals(receiver.getPhone(), n.getRecipientPhone())
                        && java.util.Objects.equals(receiver.getAcceptedUserId(), n.getPushRecipientId());
            }
            return true;
        } catch (java.io.IOException | RuntimeException ex) { return false; }
    }
}
