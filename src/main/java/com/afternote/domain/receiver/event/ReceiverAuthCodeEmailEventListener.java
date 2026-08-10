package com.afternote.domain.receiver.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ReceiverAuthCodeEmailEventListener {

    private final ReceiverAuthCodeEmailRunner runner;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRequested(ReceiverAuthCodeEmailRequestedEvent event) {
        runner.send(event);
    }
}
