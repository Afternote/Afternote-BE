package com.afternote.domain.notification.policy;

import com.afternote.domain.receiver.model.ReceiverInvitationStatus;
import com.afternote.domain.receiver.repository.ReceiverInvitationRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReceiverInvitationNotificationGuard implements NotificationDispatchGuard {
    private final ReceiverInvitationRepository invitations;
    private final ReceiverRepository receivers;
    private final ObjectMapper mapper;

    @Override
    public boolean allows(NotificationDispatch notification, LocalDateTime now) {
        try {
            Map<String, String> data = mapper.readValue(notification.getPayloadJson(), new TypeReference<>() { });
            String type = data.getOrDefault("type", "");
            if (!type.startsWith("RECEIVER_INVITATION_")) return true;
            var i = invitations.findById(Long.valueOf(data.get("invitationId"))).orElse(null);
            if (i == null || !i.getInviterUserId().equals(notification.getUserId())) return false;
            if (i.getReceiverId() != null) {
                var receiver = receivers.findById(i.getReceiverId())
                        .filter(r -> r.getUserId().equals(notification.getUserId())).orElse(null);
                if (receiver == null) return false;
                if (notification.isExternalRecipient() && !java.util.Objects.equals(receiver.getPhone(), notification.getRecipientPhone())) return false;
                if (type.equals("RECEIVER_INVITATION_PENDING_7D") && receiver.getAcceptedUserId() != null) return false;
            }
            return switch (type) {
                case "RECEIVER_INVITATION_SENT" -> i.getStatus() == ReceiverInvitationStatus.PENDING && !i.isExpired(now);
                case "RECEIVER_INVITATION_PENDING_7D" -> i.getStatus() == ReceiverInvitationStatus.PENDING
                        && !i.isExpired(now) && i.getCreatedAt() != null && !now.isBefore(i.getCreatedAt().plusDays(7));
                case "RECEIVER_INVITATION_ACCEPTED" -> i.getStatus() == ReceiverInvitationStatus.ACCEPTED;
                case "RECEIVER_INVITATION_DECLINED" -> i.getStatus() == ReceiverInvitationStatus.DECLINED;
                case "RECEIVER_INVITATION_EXPIRED" -> i.getStatus() == ReceiverInvitationStatus.EXPIRED;
                default -> false;
            };
        } catch (java.io.IOException | RuntimeException ex) { return false; }
    }
}
