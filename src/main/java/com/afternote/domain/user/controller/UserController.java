package com.afternote.domain.user.controller;

import com.afternote.domain.user.dto.*;
import com.afternote.domain.user.service.UserService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(
            summary = "내 프로필 조회 API",
            description = "로그인한 사용자의 프로필 정보를 조회합니다."
    )
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
            description = "로그인한 사용자의 푸시 알림 수신 설정을 불러옵니다."
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
            description = "로그인한 사용자의 푸시 알림 수신 설정을 수정합니다."
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
            description = "로그인한 사용자의 계정을 삭제합니다. 모든 데이터가 영구적으로 삭제되며 복구할 수 없습니다."
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


}
