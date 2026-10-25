package com.afternote.domain.notification.reminder;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import com.afternote.domain.notification.policy.NotificationTiming;
import io.swagger.v3.oas.annotations.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="200", description="성공"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400", description="잘못된 요청 (1400)"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401", description="인증 필요"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404", description="본인 대상 또는 링크를 찾을 수 없음")
})
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/time-letters")
public class TimeLetterDeliveryController {
    private final TimeLetterDeliveryService service;
    private final Clock clock;
    @Operation(summary = "수신자별 편지 전달 상태", description = "본인 편지만 조회. DELIVERED·UNACCEPTED·ACCOUNT_INACTIVE·PENDING과 실제 전달 시각을 반환합니다. 푸시 발송 결과와 구분됩니다.")
    @GetMapping("/{id}/delivery-status") public ApiResponse<java.util.List<TimeLetterDeliveryService.RecipientState>> status(
            @Parameter(hidden=true) @UserId Long userId,@PathVariable Long id) {
        return ApiResponse.success(service.status(userId,id));
    }
    public record Retry(@NotNull UUID idempotencyKey) { }
    @Operation(summary = "타임레터 전달 재시도", description = "본인 편지의 발송 시각 경과 후 실패한 수신자만 재평가합니다. 이미 전달된 대상은 재전달하지 않습니다. 같은 UUID 재요청은 최초 결과를 반환합니다. delivered/failed는 앱에서 열람 가능한 전달 결과이며 푸시 수신 결과가 아닙니다.")
    @PostMapping("/{id}/retry-delivery") public ApiResponse<TimeLetterDeliveryService.Result> retry(
            @Parameter(hidden = true) @UserId Long userId, @PathVariable Long id, @Valid @RequestBody Retry retry) {
        return ApiResponse.success(service.deliver(id, userId, retry.idempotencyKey(), LocalDateTime.now(clock.withZone(NotificationTiming.ZONE))));
    }
}
