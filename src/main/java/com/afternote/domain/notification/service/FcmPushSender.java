package com.afternote.domain.notification.service;

import com.afternote.domain.notification.model.UserPushToken;
import com.afternote.domain.notification.repository.UserPushTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmPushSender {

    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final UserPushTokenRepository userPushTokenRepository;

    public boolean isEnabled() {
        return firebaseMessagingProvider.getIfAvailable() != null;
    }

    /**
     * 사용자의 등록된 모든 기기로 푸시를 보낸다. FCM 미설정·발송 실패는 본 트랜잭션을 실패시키지 않는다.
     */
    public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        if (firebaseMessaging == null) {
            log.debug("FCM not configured; skip push. userId={}", userId);
            return;
        }

        List<UserPushToken> tokens = userPushTokenRepository.findAllByUser_Id(userId);
        if (tokens.isEmpty()) {
            log.debug("No push tokens for userId={}", userId);
            return;
        }

        for (UserPushToken pushToken : tokens) {
            sendOne(firebaseMessaging, userId, pushToken, title, body, data);
        }
    }

    private void sendOne(
            FirebaseMessaging firebaseMessaging,
            Long userId,
            UserPushToken pushToken,
            String title,
            String body,
            Map<String, String> data
    ) {
        Message.Builder builder = Message.builder()
                .setToken(pushToken.getToken())
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());

        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }

        try {
            firebaseMessaging.send(builder.build());
        } catch (FirebaseMessagingException e) {
            if (isUnregistered(e)) {
                userPushTokenRepository.delete(pushToken);
                log.info("Removed invalid FCM token. userId={}, tokenId={}", userId, pushToken.getId());
                return;
            }
            log.warn("FCM send failed. userId={}, tokenId={}, code={}",
                    userId,
                    pushToken.getId(),
                    e.getMessagingErrorCode(),
                    e);
        }
    }

    private static boolean isUnregistered(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED
                || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
