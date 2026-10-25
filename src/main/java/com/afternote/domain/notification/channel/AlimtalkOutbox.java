package com.afternote.domain.notification.channel;

import com.afternote.domain.notification.policy.*;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.user.model.UserStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class AlimtalkOutbox {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.afternote.domain.notification.policy.NotificationReadiness readiness;

    private final NotificationChannelAttemptRepository attempts;
    private final NotificationDispatchRepository dispatches;
    private final UserRepository users;
    private final List<NotificationDispatchGuard> guards;
    private final ObjectProvider<AlimtalkGateway> gateways;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private org.springframework.transaction.support.TransactionTemplate template() {
        var t = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        t.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        t.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return t;
    }

    public void pushStarted(Long dispatchId) {
        template().executeWithoutResult(s -> {
            var n = dispatches.findById(dispatchId).orElseThrow(); users.findByIdForPushUpdate(n.getUserId());
            var a = attempts.findByDispatchIdAndChannel(dispatchId, "PUSH").orElseGet(() -> attempts.save(new NotificationChannelAttempt(dispatchId, "PUSH")));
            a.claim();
        });
    }
    public void pushFinished(Long dispatchId, String result) {
        template().executeWithoutResult(s -> attempts.findByDispatchIdAndChannel(dispatchId, "PUSH")
                .ifPresent(a -> a.result(result, null, null)));
    }
    public void enqueue(Long dispatchId) {
        template().executeWithoutResult(s -> {
            var n = dispatches.findById(dispatchId).orElseThrow();
            users.findByIdForPushUpdate(n.getUserId());
            if (attempts.findByDispatchIdAndChannel(dispatchId, "ALIMTALK").isEmpty()) {
                var attempt = new NotificationChannelAttempt(dispatchId, "ALIMTALK");
                attempt.result("PENDING", null, now()); attempts.save(attempt);
            }
        });
        process(dispatchId);
    }
    @Scheduled(fixedDelayString = "${notification.alimtalk.poll-ms:60000}")
    public void poll() {
        if (readiness != null && !readiness.isReady()) return;
        for (var attempt : attempts.findTop200ByNextAttemptAtLessThanEqualOrderById(now())) process(attempt.getDispatchId());
    }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(NotificationTiming.ZONE)); }
    public void process(Long dispatchId) {
        NotificationDispatch n = template().execute(s -> {
            var dispatch = dispatches.findById(dispatchId).orElse(null);
            if (dispatch == null) return null;
            var user = users.findByIdForPushUpdate(dispatch.getUserId()).orElse(null);
            var a = attempts.findByDispatchIdAndChannel(dispatchId, "ALIMTALK").orElse(null);
            if (a == null || a.getNextAttemptAt() == null || a.getNextAttemptAt().isAfter(now())) return null;
            if (user == null || user.getStatus() != UserStatus.ACTIVE || guards.stream().anyMatch(g -> !g.allows(dispatch, now()))) {
                a.result("CANCELLED", null, null);
                dispatch.finish(dispatch.isPushAccepted() ? NotificationDispatch.Status.PUSH_SENT : NotificationDispatch.Status.CANCELLED,
                        "ALIMTALK_CANCELLED_SOURCE_CHANGED"); return null;
            }
            if (!dispatch.isExternalRecipient()) {
                dispatch.channels(dispatch.effectiveChannel(), user.getPhone(), dispatch.getPushRecipientId(), false);
            }
            LocalDateTime allowed = NotificationTiming.allowedAt(dispatch.getCategory(), now());
            if (allowed.isAfter(now())) { a.result("PENDING", null, allowed); return null; }
            if (dispatch.getRecipientPhone() == null || dispatch.getRecipientPhone().isBlank()) {
                a.result("NO_CONTACT", null, null); dispatch.finish(NotificationDispatch.Status.ALIMTALK_PENDING, "NO_CONTACT"); return null;
            }
            if (gateways.getIfAvailable() == null) {
                a.result("UNAVAILABLE", null, now().plusHours(1)); dispatch.finish(NotificationDispatch.Status.ALIMTALK_PENDING, "ALIMTALK_NOT_CONFIGURED"); return null;
            }
            a.claim(); return dispatch;
        });
        if (n == null) return;
        AlimtalkGateway.Result result;
        try {
            Map<String, String> navigation = mapper.readValue(n.getPayloadJson(), new TypeReference<>() { });
            result = Objects.requireNonNull(gateways.getObject().send("notification:" + n.getId(), n.getRecipientPhone(),
                    navigation.getOrDefault("type", "UNKNOWN"), n.getBody(), navigation));
        } catch (Exception ex) { result = new AlimtalkGateway.Result(AlimtalkGateway.Outcome.UNKNOWN, null); }
        var outcome = result;
        template().executeWithoutResult(s -> {
            var dispatch = dispatches.findById(dispatchId).orElseThrow();
            users.findByIdForPushUpdate(dispatch.getUserId());
            var a = attempts.findByDispatchIdAndChannel(dispatchId, "ALIMTALK").orElseThrow();
            LocalDateTime retry = outcome.outcome() == AlimtalkGateway.Outcome.RETRYABLE_REJECTION && a.getAttempts() < 3
                    ? now().plusMinutes(5L * a.getAttempts()) : outcome.outcome() == AlimtalkGateway.Outcome.UNAVAILABLE ? now().plusHours(1) : null;
            a.result(outcome.outcome().name(), outcome.providerMessageId(), retry);
            dispatch.finish(outcome.outcome() == AlimtalkGateway.Outcome.ACCEPTED ? NotificationDispatch.Status.ALIMTALK_SENT
                    : NotificationDispatch.Status.ALIMTALK_PENDING, "ALIMTALK_" + outcome.outcome());
        });
    }
}
