package com.afternote.domain.notification.engagement;

import com.afternote.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationActivityService {
    private final UserRepository users;
    private final NotificationProfileRepository profiles;
    @Transactional
    public void capture(Long id, LocalDateTime now) {
        var user = users.findByIdForPushUpdate(id).orElse(null);
        if (user == null) return;
        var profile = profiles.findById(id).orElseGet(() -> profiles.save(new NotificationProfile(id)));
        profile.captureActivity(user.getLastActiveAt(), now);
    }
}
