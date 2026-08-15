package com.afternote.domain.user.event;

import com.afternote.domain.user.service.ActivityTouchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserActivityTouchedEventListener {

    private final ActivityTouchService activityTouchService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTouched(UserActivityTouchedEvent event) {
        activityTouchService.touch(event.userId());
    }
}
