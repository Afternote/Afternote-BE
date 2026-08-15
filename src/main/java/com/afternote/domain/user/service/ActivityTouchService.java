package com.afternote.domain.user.service;

import com.afternote.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 미사용(INACTIVITY) 판정용 last_active_at 갱신.
 * User 엔티티를 dirty 하지 않고 bulk UPDATE만 수행해 콘텐츠 INSERT와 데드락을 피한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityTouchService {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touch(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            int updated = userRepository.updateLastActiveAt(userId, now);
            if (updated == 0) {
                log.debug("Activity touch skipped — user not found: {}", userId);
            }
        } catch (Exception e) {
            // 콘텐츠는 이미 커밋된 뒤일 수 있으므로 활동 갱신 실패는 로그만
            log.warn("Failed to touch activity for user {}: {}", userId, e.getMessage());
        }
    }
}
