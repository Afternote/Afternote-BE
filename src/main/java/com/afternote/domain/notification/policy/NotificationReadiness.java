package com.afternote.domain.notification.policy;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Scheduled external sends begin only after ApplicationRunner data upgrades have completed. */
@Component
public class NotificationReadiness {
    private volatile boolean ready;
    @EventListener public void onReady(ApplicationReadyEvent event) { ready = true; }
    public boolean isReady() { return ready; }
}
