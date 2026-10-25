package com.afternote.domain.notification.engagement;
import com.afternote.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403", description="권한 또는 링크 목적 불일치"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="본인 대상 또는 링크를 찾을 수 없음"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="410", description="만료되거나 종료된 회차의 링크")
})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notification-links")
public class NotificationLinkController {
    private final EngagementNotificationService service;
    @SecurityRequirements
    @Operation(summary = "알림 웹 링크 유효성 조회", description = "불투명 토큰이 필요합니다. 원본 미접속 회차·대리인 버전·만료를 검증하며 기록 내용이나 연락처는 반환하지 않습니다.")
    @GetMapping("/{token}") public ApiResponse<EngagementNotificationService.LinkInfo> get(@PathVariable String token) {
        return ApiResponse.success(service.link(token));
    }
    @Operation(summary = "보관 확인·절차 취소", description = "로그인한 본인과 현재 회차의 보관 확인 링크만 허용합니다. 활동 시각을 갱신하고 알림 절차를 취소하며 기존 전달 설정을 변경하지 않습니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="본인 로그인 필요")
    @PostMapping("/{token}/cancel") public ApiResponse<EngagementNotificationService.Entry> cancel(
            @io.swagger.v3.oas.annotations.Parameter(hidden=true) @com.afternote.global.resolver.UserId Long userId, @PathVariable String token) {
        return ApiResponse.success(service.cancel(token,userId));
    }
    @SecurityRequirements
    @Operation(summary = "대리인 확인 접수", description = "대리인 확인 목적의 유효한 토큰만 허용합니다. 동일 링크 재요청은 멱등. 접수 후 7일간 안내만 보내며 기존 전달 설정은 변경하지 않습니다. 본인이 앱을 열면 이 회차는 취소됩니다.")
    @PostMapping("/{token}/confirm") public ApiResponse<EngagementNotificationService.LinkInfo> confirm(@PathVariable String token) {
        return ApiResponse.success(service.confirm(token));
    }
}
