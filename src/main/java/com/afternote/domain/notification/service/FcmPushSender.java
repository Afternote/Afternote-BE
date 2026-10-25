package com.afternote.domain.notification.service;

import com.afternote.domain.notification.model.UserPushToken;
import com.afternote.domain.notification.repository.UserPushTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.AndroidConfig;
import java.time.LocalDateTime;
import com.afternote.domain.notification.dto.PushPayload;
import com.afternote.domain.notification.dto.PushDeliveryResult;
import com.afternote.domain.notification.model.NotificationPermission;
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

    private final com.afternote.domain.notification.repository.PushInstallationRepository installationRepository;
    private final ObjectProvider<FirebaseMessaging> firebaseMessagingProvider;
    private final UserPushTokenRepository userPushTokenRepository;

    public boolean isEnabled() {
        return firebaseMessagingProvider.getIfAvailable() != null;
    }

    /**
     * 사용자의 등록된 모든 기기로 푸시를 보낸다. FCM 미설정·발송 실패는 본 트랜잭션을 실패시키지 않는다.
     */
    public void sendToUser(Long userId, String title, String body, Map<String, String> data) {
        sendToUserWithResult(userId, title, body, data);
    }

    public PushDeliveryResult sendToUserWithResult(Long userId, String title, String body, Map<String, String> data) {
        Map<String, String> payload = PushPayload.create(title, body, data);
        boolean denied = installationRepository.existsByUser_IdAndPermission(userId, NotificationPermission.DENIED);
        List<UserPushToken> tokens = userPushTokenRepository.findAllByUser_Id(userId);
        denied |= tokens.stream().anyMatch(token -> token.effectivePermission() == NotificationPermission.DENIED);
        FirebaseMessaging firebaseMessaging = firebaseMessagingProvider.getIfAvailable();
        if (firebaseMessaging == null) {
            log.debug("FCM not configured; skip push. userId={}", userId);
            return new PushDeliveryResult(0, 0, denied, false);
        }

        if (tokens.isEmpty()) {
            log.debug("No push tokens for userId={}", userId);
            return new PushDeliveryResult(0, 0, denied, true);
        }

        int sent = 0;
        int failed = 0;
        var failure = com.afternote.domain.notification.dto.PushDeliveryResult.Failure.NO_TOKEN;
        for (UserPushToken pushToken : tokens) {
            if (pushToken.effectivePermission() == NotificationPermission.DENIED) {
                denied = true;
            } else if (pushToken.isPushEnabled()) {
                var outcome = sendOne(firebaseMessaging, userId, pushToken, payload);
                if (outcome == com.afternote.domain.notification.dto.PushDeliveryResult.Failure.NONE) sent++;
                else { failed++; failure = outcome; }
            }
        }
        return new PushDeliveryResult(sent, failed, denied, true, sent > 0 ? PushDeliveryResult.Failure.NONE : failure);
    }

    private PushDeliveryResult.Failure sendOne(
            FirebaseMessaging firebaseMessaging,
            Long userId,
            UserPushToken pushToken,
            Map<String, String> data
    ) {
        Message message = Message.builder()
                .setToken(pushToken.getToken())
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .putAllData(data)
                .build();

        try {
            firebaseMessaging.send(message);
            userPushTokenRepository.clearInvalidation(pushToken.getId(), userId);
            return PushDeliveryResult.Failure.NONE;
        } catch (FirebaseMessagingException e) {
            if (isUnregistered(e)) {
                userPushTokenRepository.markInvalid(pushToken.getId(), userId, LocalDateTime.now());
                log.info("Marked FCM token invalid. userId={}, tokenId={}", userId, pushToken.getId());
                return PushDeliveryResult.Failure.UNREGISTERED;
            }
            log.warn("FCM send failed. userId={}, tokenId={}, code={}",
                    userId,
                    pushToken.getId(),
                    e.getMessagingErrorCode());
            MessagingErrorCode code = e.getMessagingErrorCode();
            if (code == null) return PushDeliveryResult.Failure.UNKNOWN;
            return switch (code) {
                case INVALID_ARGUMENT -> PushDeliveryResult.Failure.INVALID_MESSAGE;
                case SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR -> PushDeliveryResult.Failure.CONFIGURATION;
                case QUOTA_EXCEEDED, INTERNAL, UNAVAILABLE -> PushDeliveryResult.Failure.TEMPORARY_REJECTION;
                default -> PushDeliveryResult.Failure.UNKNOWN;
            };
        }
    }

    private static boolean isUnregistered(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED;
    }
}
