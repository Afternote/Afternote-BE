package com.afternote.domain.notification.engagement;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
@Component
@RequiredArgsConstructor
@Slf4j
public class EngagementNotificationScheduler {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.afternote.domain.notification.policy.NotificationReadiness readiness;

    private final EntityManager entityManager;
    private final EngagementNotificationService service;
    @Scheduled(fixedDelayString = "${notification.engagement.poll-ms:60000}")
    public void poll() {
        if (readiness != null && !readiness.isReady()) return;
        long after = 0; var now = service.now();
        while (true) {
            List<Long> ids = entityManager.createQuery("select u.id from User u where u.id > :after and u.status = com.afternote.domain.user.model.UserStatus.ACTIVE order by u.id", Long.class)
                    .setParameter("after", after).setMaxResults(100).getResultList();
            if (ids.isEmpty()) return;
            for (Long id : ids) try { service.plan(id, now); } catch (RuntimeException ex) { log.warn("Notification planning failed userId={}", id, ex); }
            after = ids.get(ids.size()-1);
        }
    }
}
