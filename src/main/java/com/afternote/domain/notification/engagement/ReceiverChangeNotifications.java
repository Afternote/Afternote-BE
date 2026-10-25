package com.afternote.domain.notification.engagement;
import com.afternote.domain.notification.policy.*;
import com.afternote.domain.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class ReceiverChangeNotifications {
    private final UserRepository users;
    private final NotificationPolicyStore store;
    private final Clock clock;
    @Transactional public void lockOwner(Long id) { users.findByIdForPushUpdate(id); }
    @Transactional public void changed(Long owner, Long receiver) {
        store.enqueueWithChannels(owner, "RECEIVER_CHANGE:" + receiver + ":" + UUID.randomUUID(), NotificationCategory.D,
                NotificationEvent.SCHEDULED, "수신자 정보가 변경됐어요", Map.of("type", "RECEIVER_INFO_CHANGED", "destination", "RECEIVER_DETAIL",
                        "receiverId", receiver.toString(), "action", "VIEW"), LocalDateTime.now(clock.withZone(NotificationTiming.ZONE)),
                NotificationChannel.IN_APP, null, null, false);
    }
}
