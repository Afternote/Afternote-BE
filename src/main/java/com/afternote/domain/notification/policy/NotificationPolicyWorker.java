package com.afternote.domain.notification.policy;

import com.afternote.domain.notification.service.FcmPushSender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationPolicyWorker {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.afternote.domain.notification.policy.NotificationReadiness readiness;

    private final NotificationPolicyStore store;
    private final NotificationDispatchRepository repository;
    private final FcmPushSender sender;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.afternote.domain.notification.channel.AlimtalkOutbox alimtalk;


    @Scheduled(fixedDelayString = "${notification.policy.poll-ms:30000}")
    public void poll() {
        if (readiness != null && !readiness.isReady()) return;
        for (Long userId : repository.findDueUsers(LocalDateTime.now(clock.withZone(NotificationTiming.ZONE)), java.util.List.of(NotificationDispatch.Status.QUEUED, NotificationDispatch.Status.READY), PageRequest.of(0, 200))) {
            try { process(userId); }
            catch (RuntimeException e) { log.warn("Notification policy processing failed for userId={}", userId, e); }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(NotificationQueued event) {
        if (readiness == null || readiness.isReady()) process(event.userId());
    }

    public void process(Long userId) {
        for (Long id : store.prepare(userId, LocalDateTime.now(clock.withZone(NotificationTiming.ZONE)))) {
            store.claim(id, LocalDateTime.now(clock.withZone(NotificationTiming.ZONE))).ifPresent(this::deliver);
        }
    }

    private void deliver(NotificationDispatch n) {
        try {
            Map<String, String> data = new HashMap<>(objectMapper.readValue(n.getPayloadJson(), new TypeReference<Map<String, String>>() { }));
            data.put("notificationId", n.getId().toString());
            if (n.effectiveChannel() == NotificationChannel.ALIMTALK) {
                store.complete(n.getId(), NotificationDispatch.Status.ALIMTALK_PENDING, "ALIMTALK_QUEUED");
                if (alimtalk != null) alimtalk.enqueue(n.getId());
                return;
            }
            Long recipient = n.isExternalRecipient() ? n.getPushRecipientId() : n.getUserId();
            if (alimtalk != null) alimtalk.pushStarted(n.getId());
            var result = recipient == null ? new com.afternote.domain.notification.dto.PushDeliveryResult(0, 0, false, true)
                    : sender.sendToUserWithResult(recipient, n.getTitle(), n.getBody(), data);
            if (alimtalk != null) alimtalk.pushFinished(n.getId(), result.delivered() ? "ACCEPTED" : result.failure().name());
            if (result.delivered()) store.complete(n.getId(), NotificationDispatch.Status.PUSH_SENT, null);
            else store.complete(n.getId(), NotificationDispatch.Status.ALIMTALK_PENDING,
                    result.permissionDenied() ? "PERMISSION_DENIED" : !result.configured() ? "FCM_NOT_CONFIGURED" : "PUSH_UNAVAILABLE");
            if (alimtalk != null && (n.effectiveChannel() == NotificationChannel.PUSH_AND_ALIMTALK
                    || (!result.delivered() && result.allowsAlimtalkFallback()))) alimtalk.enqueue(n.getId());
        } catch (Exception e) {
            // Provider may have accepted before a timeout. Do not replay an ambiguous push.
            if (alimtalk != null) alimtalk.pushFinished(n.getId(), "UNKNOWN");
            store.complete(n.getId(), NotificationDispatch.Status.PUSH_FAILED, "DELIVERY_OUTCOME_UNKNOWN");
            log.warn("Notification delivery failed for notificationId={}", n.getId(), e);
        }
    }
}
