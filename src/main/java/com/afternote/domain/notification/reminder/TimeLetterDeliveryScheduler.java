package com.afternote.domain.notification.reminder;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.afternote.domain.notification.policy.NotificationTiming;
@Component
@RequiredArgsConstructor
@Slf4j
public class TimeLetterDeliveryScheduler {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.afternote.domain.notification.policy.NotificationReadiness readiness;

    private final EntityManager entityManager;
    private final TimeLetterDeliveryService service;
    private final Clock clock;
    @Scheduled(fixedDelayString = "${notification.delivery.poll-ms:60000}")
    public void poll() {
        if (readiness != null && !readiness.isReady()) return;
        var now = LocalDateTime.now(clock.withZone(NotificationTiming.ZONE));
        scan("select t.id from TimeLetter t where t.id > :after and t.user.status = com.afternote.domain.user.model.UserStatus.ACTIVE "
                + "and t.status = com.afternote.domain.timeletter.model.TimeLetterStatus.SCHEDULED and t.deliveryMode = com.afternote.domain.timeletter.model.TimeLetterDeliveryMode.DATE order by t.id", id -> {
            service.reminder3(id, now); service.deliver(id, null, null, now);
        });
        scan("select c.id from DeliveryCondition c where c.id > :after and c.state = com.afternote.domain.delivery.model.ConditionState.FULFILLED order by c.id", id -> service.fulfilledCondition(id, now));
    }
    private void scan(String query, java.util.function.Consumer<Long> process) {
        long after = 0;
        while (true) {
            var ids = entityManager.createQuery(query, Long.class).setParameter("after", after).setMaxResults(200).getResultList();
            if (ids.isEmpty()) return;
            for (Long id : ids) try { process.accept(id); } catch (RuntimeException ex) { log.warn("Letter notification processing failed id={}", id, ex); }
            after = ids.get(ids.size()-1);
        }
    }
}
