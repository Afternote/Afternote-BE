package com.afternote.domain.notification.service;

import com.afternote.domain.admin.service.AdminService;
import com.afternote.domain.notification.dto.AdminPushTestRequest;
import com.afternote.domain.notification.repository.UserPushTokenRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminPushTestServiceTest {

    @InjectMocks
    private AdminPushTestService adminPushTestService;

    @Mock
    private AdminService adminService;

    @Mock
    private FcmPushSender fcmPushSender;

    @Mock
    private UserPushTokenRepository userPushTokenRepository;

    @Test
    @DisplayName("관리자가 대상 사용자 토큰으로 테스트 푸시를 보낸다")
    void sendTest_SendsToTargetUser() {
        given(fcmPushSender.isEnabled()).willReturn(true);
        given(userPushTokenRepository.countByUser_Id(2L)).willReturn(1L);

        var response = adminPushTestService.sendTest(
                1L,
                new AdminPushTestRequest(2L, "제목", "본문")
        );

        assertThat(response.targetUserId()).isEqualTo(2L);
        assertThat(response.tokenCount()).isEqualTo(1);
        verify(adminService).validateAdmin(1L);
        verify(fcmPushSender).sendToUser(eq(2L), eq("제목"), eq("본문"), eq(Map.of("type", "TEST")));
    }

    @Test
    @DisplayName("userId 생략 시 관리자 본인에게 보낸다")
    void sendTest_DefaultsToAdmin() {
        given(fcmPushSender.isEnabled()).willReturn(true);
        given(userPushTokenRepository.countByUser_Id(1L)).willReturn(2L);

        var response = adminPushTestService.sendTest(1L, null);

        assertThat(response.targetUserId()).isEqualTo(1L);
        assertThat(response.tokenCount()).isEqualTo(2);
        verify(fcmPushSender).sendToUser(eq(1L), eq("AfterNote"), eq("서버에서 보낸 테스트 푸시입니다."), eq(Map.of("type", "TEST")));
    }

    @Test
    @DisplayName("FCM 미설정이면 503/2600")
    void sendTest_FcmNotConfigured() {
        given(fcmPushSender.isEnabled()).willReturn(false);

        assertThatThrownBy(() -> adminPushTestService.sendTest(1L, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FCM_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("등록 토큰이 없으면 404/2601")
    void sendTest_NoTokens() {
        given(fcmPushSender.isEnabled()).willReturn(true);
        given(userPushTokenRepository.countByUser_Id(1L)).willReturn(0L);

        assertThatThrownBy(() -> adminPushTestService.sendTest(1L, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PUSH_TOKEN_NOT_FOUND);
    }
}
