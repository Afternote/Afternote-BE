package com.afternote.domain.notification.service;

import com.afternote.domain.notification.dto.DeletePushTokenRequest;
import com.afternote.domain.notification.dto.PushTokenResponse;
import com.afternote.domain.notification.dto.RegisterPushTokenRequest;
import com.afternote.domain.notification.model.PushPlatform;
import com.afternote.domain.notification.model.UserPushToken;
import com.afternote.domain.notification.repository.UserPushTokenRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPushTokenService {

    private final UserRepository userRepository;
    private final UserPushTokenRepository userPushTokenRepository;

    @Transactional
    public PushTokenResponse registerOrRefresh(Long userId, RegisterPushTokenRequest request) {
        User user = findUserById(userId);
        String token = request.token().trim();
        PushPlatform platform = request.platform();
        LocalDateTime now = LocalDateTime.now();

        UserPushToken existing = userPushTokenRepository.findByToken(token).orElse(null);
        if (existing == null) {
            UserPushToken created = UserPushToken.builder()
                    .user(user)
                    .token(token)
                    .platform(platform)
                    .lastSeenAt(now)
                    .build();
            userPushTokenRepository.save(created);
            return PushTokenResponse.from(created);
        }

        if (!existing.getUser().getId().equals(userId)) {
            existing.reassignTo(user, platform, now);
        } else {
            existing.touch(platform, now);
        }

        return PushTokenResponse.from(existing);
    }

    @Transactional
    public void unregister(Long userId, DeletePushTokenRequest request) {
        String token = request.token().trim();
        userPushTokenRepository.deleteByUser_IdAndToken(userId, token);
    }

    @Transactional
    public void deleteAllForUser(Long userId) {
        userPushTokenRepository.deleteByUser_Id(userId);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
