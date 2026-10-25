package com.afternote.domain.notification.channel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
public interface NotificationChannelAttemptRepository extends JpaRepository<NotificationChannelAttempt, Long> {
    Optional<NotificationChannelAttempt> findByDispatchIdAndChannel(Long dispatchId, String channel);
    List<NotificationChannelAttempt> findTop200ByNextAttemptAtLessThanEqualOrderById(LocalDateTime now);
}
