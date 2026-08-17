package com.afternote.domain.user.controller;

import com.afternote.domain.notification.dto.DeletePushTokenRequest;
import com.afternote.domain.notification.dto.PushTokenResponse;
import com.afternote.domain.notification.dto.RegisterPushTokenRequest;
import com.afternote.domain.notification.service.UserPushTokenService;
import com.afternote.domain.user.dto.*;
import com.afternote.domain.user.service.UserService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User API", description = "회원 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserPushTokenService userPushTokenService;

    @Operation(
            summary = "내 프로필 조회 API",
            description = "로그인한 사용자의 프로필 정보를 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "프로필 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (code: 1000)")
    })
    @GetMapping("/me")
    public ApiResponse<UserResponse> getMyProfile(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(
                userService.getMyProfile(userId)
        );
    }

    @Operation(
            summary = "프로필 수정 API",
            description = "로그인한 사용자의 프로필 정보를 수정합니다."
    )
    @PatchMapping("/me")
    public ApiResponse<UserResponse> updateMyProfile(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody UserUpdateProfileRequest request
    ) {
        return ApiResponse.success(
                userService.updateMyProfile(userId, request)
        );
    }

    @Operation(
            summary = "푸시 알림 설정 조회 API",
            description = "서비스 알림 3종(타임레터·마음기록·애프터노트) 수신 설정입니다. 마케팅 SMS/이메일/푸시는 GET /marketing-consents 입니다."
    )
    @GetMapping("/push-settings")
    public ApiResponse<UserPushSettingResponse> getMyPushSettings(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(
                userService.getMyPushSettings(userId)
        );
    }

    @Operation(
            summary = "연결된 계정 조회 API",
            description = "로그인한 사용자의 연결된 소셜/로컬 계정 정보를 조회합니다."
    )
    @GetMapping("/connected-accounts")
    public ApiResponse<UserConnectedAccountResponse> getConnectedAccounts(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(
                userService.getConnectedAccounts(userId)
        );
    }

    @Operation(
            summary = "연결 계정 추가(소셜 연동) API",
            description = """
                    로그인한 사용자에게 소셜 제공자를 추가로 연결합니다.
                    클라이언트는 해당 제공자 SDK로 받은 OAuth2 access token을 전달합니다.
                    provider: KAKAO, GOOGLE, NAVER, APPLE (구현된 제공자만 성공)
                    """
    )
    @PostMapping("/connected-accounts/{provider}")
    public ApiResponse<UserConnectedAccountResponse> linkConnectedAccount(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable String provider,
            @Valid @RequestBody SocialAccountLinkRequest request
    ) {
        return ApiResponse.success(
                userService.linkConnectedAccount(userId, provider, request)
        );
    }

    @Operation(
            summary = "연결 계정 해제 API",
            description = """
                    연결된 소셜 제공자 연동을 해제합니다. LOCAL(이메일 가입)은 해제할 수 없습니다.
                    비밀번호가 없는 순수 소셜 계정은 마지막 남은 로그인 수단을 해제할 수 없습니다.
                    """
    )
    @DeleteMapping("/connected-accounts/{provider}")
    public ApiResponse<UserConnectedAccountResponse> unlinkConnectedAccount(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable String provider
    ) {
        return ApiResponse.success(
                userService.unlinkConnectedAccount(userId, provider)
        );
    }

    @Operation(
            summary = "푸시 알림 설정 수정 API",
            description = "서비스 알림 3종만 수정합니다. 마케팅 채널 동의는 PATCH /marketing-consents 입니다."
    )
    @PatchMapping("/push-settings")
    public ApiResponse<UserPushSettingResponse> updateMyPushSettings(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody UserUpdatePushSettingRequest request
    ) {
        return ApiResponse.success(
                userService.updateMyPushSettings(userId, request)
        );
    }

    @Operation(
            summary = "마케팅 수신 동의 조회 API",
            description = """
                    마케팅·광고 알림 채널(문자·이메일·푸시) 수신 동의를 조회합니다.
                    가입·비밀번호 찾기·수신자 인증 메일과 서비스 알림 3종(/push-settings)은 이 값과 무관합니다.
                    """
    )
    @GetMapping("/marketing-consents")
    public ApiResponse<UserMarketingConsentResponse> getMyMarketingConsents(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(userService.getMyMarketingConsents(userId));
    }

    @Operation(
            summary = "마케팅 수신 동의 수정 API",
            description = """
                    보낸 채널만 변경합니다. 생략한 필드는 유지됩니다.
                    동의해도 지금 당장 광고 메일/문자가 나가지 않습니다. 나중에 마케팅 발송 시 이 값을 봅니다.
                    마케팅 이메일은 기존 SMTP(EmailService와 동일 파이프)를 재사용할 예정이며, 인증 메일은 계속 동의 없이 발송됩니다.
                    """
    )
    @PatchMapping("/marketing-consents")
    public ApiResponse<UserMarketingConsentResponse> updateMyMarketingConsents(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody UserUpdateMarketingConsentRequest request
    ) {
        return ApiResponse.success(userService.updateMyMarketingConsents(userId, request));
    }

    @Operation(
            summary = "FCM 기기 토큰 등록·갱신 API",
            description = """
                    로그인한 사용자의 FCM registration token을 등록하거나 갱신합니다.
                    앱 최초 실행·로그인·onNewToken 시 호출합니다. 동일 token은 upsert(멱등)됩니다.
                    """
    )
    @PutMapping("/push-tokens")
    public ApiResponse<PushTokenResponse> registerPushToken(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody RegisterPushTokenRequest request
    ) {
        return ApiResponse.success(
                userPushTokenService.registerOrRefresh(userId, request)
        );
    }

    @Operation(
            summary = "FCM 기기 토큰 해제 API",
            description = "로그아웃·알림 권한 철회 시 해당 기기 token을 서버에서 제거합니다. 없는 token도 200(멱등)입니다."
    )
    @DeleteMapping("/push-tokens")
    public ApiResponse<Void> deletePushToken(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody DeletePushTokenRequest request
    ) {
        userPushTokenService.unregister(userId, request);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "수신인 목록 조회 API",
            description = "로그인한 사용자가 등록한 수신인 목록을 조회합니다."
    )
    @GetMapping("/receivers")
    public ApiResponse<List<ReceiverListResponse>> getReceivers(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(
                userService.getReceivers(userId)
        );
    }

    @Operation(
            summary = "수신자 등록 API",
            description = "로그인한 사용자가 새로운 수신자를 등록합니다."
    )
    @PostMapping("/receivers")
    public ApiResponse<UserCreateReceiverResponse> createReceiver(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody UserCreateReceiverRequest request
    ) {
        return ApiResponse.success(
                userService.createReceiver(userId, request)
        );
    }

    @Operation(
            summary = "수신인 상세 조회 API",
            description = "특정 수신인의 상세 정보를 조회합니다."
    )
    @GetMapping("/receivers/{receiverId}")
    public ApiResponse<ReceiverDetailResponse> getReceiverDetail(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(
                userService.getReceiverDetail(userId, receiverId)
        );
    }

    @Operation(
            summary = "수신자 메시지 수정 API",
            description = "특정 수신자에게 남길 메시지를 등록하거나 수정합니다."
    )
    @PatchMapping("/receivers/{receiverId}/message")
    public ApiResponse<Void> updateReceiverMessage(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @Valid @RequestBody UserUpdateReceiverMessageRequest request
    ) {
        userService.updateReceiverMessage(userId, receiverId, request);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "활동 기록(ping) API",
            description = "앱 실행 등 사용자 활동을 기록하여 미사용 자동 전달 타이머를 갱신합니다."
    )
    @PostMapping("/me/activity")
    public ApiResponse<Void> recordActivity(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        userService.recordActivity(userId);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "회원 탈퇴 API",
            description = "로그인한 사용자의 계정을 삭제합니다. 모든 데이터가 영구적으로 삭제되며, 동일 이메일은 탈퇴 후 30일간 재가입할 수 없습니다."
    )
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        userService.deleteAccount(userId);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "수신인 정보 수정 API",
            description = "특정 수신인의 이름, 관계, 전화번호, 이메일 정보를 수정합니다."
    )
    @PatchMapping("/receivers/{receiverId}")
    public ApiResponse<UserPatchReceiverResponse> updateReceiver(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @Valid @RequestBody UserPatchReceiverRequest request
    ) {
        return ApiResponse.success(
                userService.updateReceiver(userId, receiverId, request)
        );
    }

    @Operation(
            summary = "수신자 삭제 API",
            description = "등록한 수신자를 삭제합니다. 타임레터·애프터노트 등 콘텐츠에 이미 연결된 수신자는 삭제할 수 없습니다."
    )
    @DeleteMapping("/receivers/{receiverId}")
    public ApiResponse<Void> deleteReceiver(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        userService.deleteReceiver(userId, receiverId);
        return ApiResponse.success(null);
    }


}
