package com.afternote.domain.notification.service;

import com.afternote.domain.notification.model.UserPushToken;
import com.afternote.domain.notification.repository.UserPushTokenRepository;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmPushSenderTest {
    @Mock com.afternote.domain.notification.repository.PushInstallationRepository installationRepository;
    @Mock ObjectProvider<FirebaseMessaging> provider;
    @Mock FirebaseMessaging firebase;
    @Mock UserPushTokenRepository repository;
    @InjectMocks FcmPushSender sender;

    private UserPushToken token() {
        UserPushToken token = UserPushToken.builder().token("private-token").build();
        ReflectionTestUtils.setField(token, "id", 7L);
        when(provider.getIfAvailable()).thenReturn(firebase);
        when(repository.findAllByUser_Id(1L)).thenReturn(List.of(token));
        return token;
    }

    @Test void sendsDataOnlyAndClearsPreviousInvalidation() throws Exception {
        token();
        sender.sendToUser(1L, "title", "body", Map.of("type", "TEST"));
        ArgumentCaptor<Message> capture = ArgumentCaptor.forClass(Message.class);
        verify(firebase).send(capture.capture());
        assertThat(ReflectionTestUtils.getField(capture.getValue(), "notification")).isNull();
        assertThat((Map<String, String>) ReflectionTestUtils.getField(capture.getValue(), "data"))
                .containsValue("title").containsValue("HOME");
        Object config = ReflectionTestUtils.getField(capture.getValue(), "androidConfig");
        assertThat(ReflectionTestUtils.getField(config, "priority")).isEqualTo("high");
        verify(repository).clearInvalidation(7L, 1L);
    }

    @Test void unregisteredIsRetainedWithSignal() throws Exception {
        token();
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(firebase.send(any(Message.class))).thenThrow(error);
        sender.sendToUser(1L, "title", "body", Map.of());
        verify(repository).markInvalid(eq(7L), eq(1L), any());
        verify(repository, never()).delete(any());
        verify(repository, never()).clearInvalidation(any(), any());
    }

    @Test void invalidArgumentDoesNotInvalidateHealthyToken() throws Exception {
        token();
        FirebaseMessagingException error = mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.INVALID_ARGUMENT);
        when(firebase.send(any(Message.class))).thenThrow(error);
        sender.sendToUser(1L, "title", "body", Map.of());
        verify(repository, never()).markInvalid(any(), any(), any());
        verify(repository, never()).delete(any());
    }

    @Test void permissionDenialSurvivesMissingFirebaseConfiguration() {
        when(repository.findAllByUser_Id(1L)).thenReturn(List.of());
        when(installationRepository.existsByUser_IdAndPermission(1L,
                com.afternote.domain.notification.model.NotificationPermission.DENIED)).thenReturn(true);
        var result = sender.sendToUserWithResult(1L, "title", "body", Map.of());
        assertThat(result.permissionDenied()).isTrue();
        assertThat(result.configured()).isFalse();
        verifyNoInteractions(firebase);
    }

    @Test void deniedInstallationWithoutTokenIsReported() {
        when(provider.getIfAvailable()).thenReturn(firebase);
        when(repository.findAllByUser_Id(1L)).thenReturn(List.of());
        when(installationRepository.existsByUser_IdAndPermission(1L,
                com.afternote.domain.notification.model.NotificationPermission.DENIED)).thenReturn(true);
        var result = sender.sendToUserWithResult(1L, "title", "body", Map.of());
        assertThat(result.permissionDenied()).isTrue();
        assertThat(result.delivered()).isFalse();
        verifyNoInteractions(firebase);
    }

    @Test void deniedTokenIsNeverSent() {
        UserPushToken token = token();
        token.updatePermission(com.afternote.domain.notification.model.NotificationPermission.DENIED);
        var result = sender.sendToUserWithResult(1L, "title", "body", Map.of());
        assertThat(result.permissionDenied()).isTrue();
        assertThat(result.sentCount()).isZero();
        verifyNoInteractions(firebase);
    }

    @Test void invalidTokenIsNotRetriedUntilRegistration() throws Exception {
        UserPushToken token = token();
        ReflectionTestUtils.setField(token, "pushEnabled", false);
        sender.sendToUser(1L, "title", "body", Map.of());
        verifyNoInteractions(firebase);
    }
}
