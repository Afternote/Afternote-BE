package com.afternote.domain.notification.policy;

import com.afternote.domain.notification.service.FcmPushSender;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
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
    private final NotificationPolicyStore store;
    private final NotificationDispatchRepository repository;
    private final FcmPushSender sender;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${notification.policy.poll-ms:30000}")
    public void poll() {
        for (Long userId : repository.findDueUsers(LocalDateTime.now(NotificationTiming.ZONE), java.util.List.of(NotificationDispatch.Status.QUEUED, NotificationDispatch.Status.READY), PageRequest.of(0, 200))) {
            try { process(userId); }
            catch (RuntimeException e) { log.warn("Notification policy processing failed for userId={}", userId, e); }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQueued(NotificationQueued event) { process(event.userId()); }

    public void process(Long userId) {
        for (Long id : store.prepare(userId, LocalDateTime.now(NotificationTiming.ZONE))) {
            store.claim(id, LocalDateTime.now(NotificationTiming.ZONE)).ifPresent(this::deliver);
        }
    }

    private void deliver(NotificationDispatch n) {
        try {
            Map<String, String> data = new HashMap<>(objectMapper.readValue(n.getPayloadJson(), new TypeReference<Map<String, String>>() { }));
            data.put("notificationId", n.getId().toString());
            var result = sender.sendToUserWithResult(n.getUserId(), n.getTitle(), n.getBody(), data);
            if (result.delivered()) store.complete(n.getId(), NotificationDispatch.Status.PUSH_SENT, null);
            else store.complete(n.getId(), NotificationDispatch.Status.ALIMTALK_PENDING,
                    result.permissionDenied() ? "PERMISSION_DENIED" : !result.configured() ? "FCM_NOT_CONFIGURED" : "PUSH_UNAVAILABLE");
        } catch (Exception e) {
            // Provider may have accepted before a timeout. Do not replay an ambiguous push.
            store.complete(n.getId(), NotificationDispatch.Status.PUSH_FAILED, "DELIVERY_OUTCOME_UNKNOWN");
            log.warn("Notification delivery failed for notificationId={}", n.getId(), e);
        }
    }
}
