package com.afternote.domain.receiver.service;

import com.afternote.domain.receiver.repository.ReceiverInvitationRepository;
import com.afternote.domain.notification.policy.NotificationTiming;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiverInvitationScheduler {
    private final ReceiverInvitationRepository invitations;
    private final ReceiverInvitationService service;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${notification.invitation.poll-ms:60000}")
    public void poll() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(NotificationTiming.ZONE));
        long afterId = 0;
        while (true) {
            var ids = invitations.findLifecycleCandidates(afterId, now.minusDays(7), now, PageRequest.of(0, 200));
            if (ids.isEmpty()) return;
            for (Long id : ids) {
                try { service.processPending(id, now); }
                catch (RuntimeException ex) { log.warn("Invitation lifecycle failed invitationId={}", id, ex); }
            }
            afterId = ids.get(ids.size() - 1);
        }
    }
}
