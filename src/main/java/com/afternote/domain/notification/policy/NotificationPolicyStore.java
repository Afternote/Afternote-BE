package com.afternote.domain.notification.policy;

import com.afternote.domain.notification.dto.PushPayload;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;

@Service
@RequiredArgsConstructor
public class NotificationPolicyStore {
    private final NotificationDispatchRepository repository;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final List<NotificationDispatchGuard> deliveryGuards;

    /** Internal producer API. Event keys must identify a single domain event, not a delivery attempt. */
    @Transactional
    public Long enqueue(Long userId, String eventKey, NotificationCategory category, NotificationEvent event,
            String title, String body, Map<String, String> data, LocalDateTime requestedAt) {
        Objects.requireNonNull(category); Objects.requireNonNull(event); Objects.requireNonNull(requestedAt);
        if (eventKey == null || eventKey.isBlank() || eventKey.length() > 160
                || title == null || title.length() > 200 || body == null || body.length() > 1000) {
            throw new IllegalArgumentException("Invalid notification key/title/body");
        }
        Map<String, String> validated = new HashMap<>(PushPayload.create(title, body, data));
        validated.remove("title"); validated.remove("body"); validated.remove("schemaVersion");
        String json;
        try { json = objectMapper.writeValueAsString(validated); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid notification data", e); }
        if ((json + title + body).getBytes(StandardCharsets.UTF_8).length > 3500) {
            throw new IllegalArgumentException("Notification payload is too large");
        }
        User user = lockUser(userId);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("Notification recipient must be active");
        }
        Optional<NotificationDispatch> existing = repository.findByUserIdAndEventKey(userId, eventKey);
        if (existing.isPresent()) return existing.get().getId();
        LocalDateTime now = LocalDateTime.now(clock.withZone(NotificationTiming.ZONE));
        LocalDateTime due = NotificationTiming.allowedAt(category, requestedAt.isBefore(now) ? now : requestedAt);
        NotificationDispatch notification = repository.save(new NotificationDispatch(
                userId, eventKey, category, event, title, body, json, due));
        if (event.isImmediate()) events.publishEvent(new NotificationQueued(userId));
        return notification.getId();
    }

    /** One DB lock serializes daily selection across scheduler instances and concurrent producers. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public List<Long> prepare(Long userId, LocalDateTime now) {
        User user = lockUser(userId);
        List<NotificationDispatch> due = repository.findByUserIdAndStatusAndDueAtLessThanEqual(
                userId, NotificationDispatch.Status.QUEUED, now);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            due.forEach(n -> n.finish(NotificationDispatch.Status.CANCELLED, "RECIPIENT_INACTIVE"));
            repository.findByUserIdAndStatus(userId, NotificationDispatch.Status.READY)
                    .forEach(n -> n.finish(NotificationDispatch.Status.CANCELLED, "RECIPIENT_INACTIVE"));
            return List.of();
        }
        // Release reservations belonging to deleted/changed sources before applying today's cap.
        repository.findByUserIdAndStatus(userId, NotificationDispatch.Status.READY)
                .forEach(n -> eligible(n, now));
        repository.flush();
        due.sort(Comparator.comparing(NotificationDispatch::getCategory)
                .thenComparing(NotificationDispatch::getDueAt).thenComparing(NotificationDispatch::getId));
        boolean used = repository.existsByUserIdAndSelectedDate(userId, now.toLocalDate());
        for (NotificationDispatch n : due) {
            if (!eligible(n, now)) continue;
            // Re-check current clock after outages; never deliver last night's overdue item at night.
            if (NotificationTiming.allowedAt(n.getCategory(), now).isAfter(now)) {
                n.defer(NotificationTiming.allowedAt(n.getCategory(), now));
                continue;
            }
            if (n.getEvent().isImmediate()) n.select(now);
            else if (!used) { n.select(now); used = true; }
            else n.inAppOnly(now);
        }
        repository.findByUserIdAndStatus(userId, NotificationDispatch.Status.READY).forEach(n -> {
            LocalDateTime allowed = NotificationTiming.allowedAt(n.getCategory(), now);
            if (allowed.isAfter(now)) n.defer(allowed);
        });
        repository.flush();
        return repository.findByUserIdAndStatus(userId, NotificationDispatch.Status.READY)
                .stream().filter(n -> !n.getDueAt().isAfter(now)).map(NotificationDispatch::getId).toList();
    }

    /** Commit the claim before contacting FCM. An uncertain delivery is never automatically replayed. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<NotificationDispatch> claim(Long id, LocalDateTime now) {
        NotificationDispatch n = entityManager.find(NotificationDispatch.class, id);
        if (n == null) return Optional.empty();
        User user = lockUser(n.getUserId());
        entityManager.refresh(n, LockModeType.PESSIMISTIC_WRITE);
        if (n.getStatus() != NotificationDispatch.Status.READY) return Optional.empty();
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            n.finish(NotificationDispatch.Status.CANCELLED, "RECIPIENT_INACTIVE"); return Optional.empty();
        }
        if (!eligible(n, now)) return Optional.empty();
        if (n.getDueAt().isAfter(now)) return Optional.empty();
        if (NotificationTiming.allowedAt(n.getCategory(), now).isAfter(now)) {
            n.defer(NotificationTiming.allowedAt(n.getCategory(), now));
            return Optional.empty();
        }
        if (!n.getEvent().isImmediate() && !now.toLocalDate().equals(n.getSelectedDate())) {
            if (repository.existsByUserIdAndSelectedDate(n.getUserId(), now.toLocalDate())) {
                n.inAppOnly(now); return Optional.empty();
            }
            n.select(now);
        }
        n.finish(NotificationDispatch.Status.DISPATCHING, "DELIVERY_OUTCOME_PENDING");
        return Optional.of(n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void complete(Long id, NotificationDispatch.Status status, String reason) {
        NotificationDispatch n = entityManager.find(NotificationDispatch.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (n != null && n.getStatus() == NotificationDispatch.Status.DISPATCHING) n.finish(status, reason);
    }

    private boolean eligible(NotificationDispatch n, LocalDateTime now) {
        if (deliveryGuards.stream().allMatch(guard -> guard.allows(n, now))) return true;
        n.finish(NotificationDispatch.Status.CANCELLED, "SOURCE_CHANGED_OR_DISABLED");
        return false;
    }

    private User lockUser(Long userId) {
        return entityManager.find(User.class, Objects.requireNonNull(userId), LockModeType.PESSIMISTIC_WRITE);
    }
}
