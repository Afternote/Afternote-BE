package com.afternote.domain.notification.service;

import com.afternote.domain.admin.service.AdminService;
import com.afternote.domain.notification.dto.AdminPushTestRequest;
import com.afternote.domain.notification.dto.AdminPushTestResponse;
import com.afternote.domain.notification.repository.UserPushTokenRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPushTestService {

    private static final String DEFAULT_TITLE = "AfterNote";
    private static final String DEFAULT_BODY = "서버에서 보낸 테스트 푸시입니다.";

    private final AdminService adminService;
    private final FcmPushSender fcmPushSender;
    private final UserPushTokenRepository userPushTokenRepository;

    public AdminPushTestResponse sendTest(Long adminUserId, AdminPushTestRequest request) {
        adminService.validateAdmin(adminUserId);

        Long targetUserId = request != null && request.userId() != null
                ? request.userId()
                : adminUserId;

        if (!fcmPushSender.isEnabled()) {
            throw new CustomException(ErrorCode.FCM_NOT_CONFIGURED);
        }

        long tokenCount = userPushTokenRepository.countByUser_Id(targetUserId);
        if (tokenCount == 0) {
            throw new CustomException(ErrorCode.PUSH_TOKEN_NOT_FOUND);
        }

        String title = firstNonBlank(request != null ? request.title() : null, DEFAULT_TITLE);
        String body = firstNonBlank(request != null ? request.body() : null, DEFAULT_BODY);
        fcmPushSender.sendToUser(targetUserId, title, body, Map.of("type", "TEST"));

        return AdminPushTestResponse.builder()
                .targetUserId(targetUserId)
                .tokenCount((int) tokenCount)
                .build();
    }

    private static String firstNonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
