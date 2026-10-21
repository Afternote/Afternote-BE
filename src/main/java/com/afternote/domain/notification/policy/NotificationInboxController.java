package com.afternote.domain.notification.policy;

import com.afternote.global.common.ApiResponse;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;

@Tag(name = "Notification API", description = "본인 인앱 알림함과 읽음 처리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/notifications")
public class NotificationInboxController {
    private final NotificationDispatchRepository repository;
    private final ObjectMapper objectMapper;
    @Schema(name = "NotificationInboxItem")
    public record Item(
            @Schema(description = "알림 ID", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(description = "알림 제목", requiredMode = Schema.RequiredMode.REQUIRED) String title,
            @Schema(description = "알림 본문", requiredMode = Schema.RequiredMode.REQUIRED) String body,
            @Schema(description = "화면 이동 데이터. destination, timeLetterId 등 문자열 맵", requiredMode = Schema.RequiredMode.REQUIRED)
            Map<String, String> data,
            @Schema(description = "서버 처리 상태. PUSH_SENT는 FCM 수락이며 기기 수신·읽음을 의미하지 않음. ALIMTALK_PENDING은 알림톡 미발송 대기",
                    requiredMode = Schema.RequiredMode.REQUIRED) NotificationDispatch.Status status,
            @Schema(description = "처리 사유. 정상 FCM 수락 등 사유가 없으면 null", nullable = true) String deliveryReason,
            @Schema(description = "읽음 시각. 읽기 전에는 null", nullable = true) LocalDateTime readAt
    ) { }
    @Schema(name = "NotificationInboxResponse")
    public record Inbox(
            @Schema(description = "전체 미읽음 알림 수. 현재 페이지 개수와 무관", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
            long unreadCount,
            @Schema(description = "알림 ID 내림차순, 최대 50개. 아직 노출되지 않은 알림과 취소 알림은 제외", requiredMode = Schema.RequiredMode.REQUIRED)
            List<Item> items
    ) { }

    @Operation(summary = "내 인앱 알림 조회", description = "본인 알림을 ID 내림차순으로 조회합니다. page는 0부터 시작하며 페이지 크기는 50입니다. 전체 unreadCount를 함께 반환합니다. CANCELLED 알림은 제외합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "미읽음 수와 알림 목록"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "page가 음수이거나 정수 형식이 아님 (code: 1400)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청")
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ApiResponse<Inbox> list(@Parameter(hidden = true) @UserId Long userId,
            @Parameter(description = "0부터 시작하는 페이지 번호", schema = @Schema(type = "integer", format = "int32", minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page) {
        if (page < 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        return ApiResponse.success(new Inbox(repository.countByUserIdAndVisibleAtIsNotNullAndReadAtIsNullAndStatusNot(
                userId, NotificationDispatch.Status.CANCELLED),
                repository.findByUserIdAndVisibleAtIsNotNullAndStatusNotOrderByIdDesc(
                        userId, NotificationDispatch.Status.CANCELLED, PageRequest.of(page, 50))
                        .stream().map(this::item).toList()));
    }

    @Operation(summary = "내 알림 읽음 처리", description = "본인에게 노출된 알림만 읽음 처리합니다. 이미 읽은 알림은 최초 readAt을 유지합니다. 타인·미노출·취소·없는 알림은 404/1006입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "읽음 처리 완료. data=null"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "알림 ID가 정수 형식이 아님 (code: 1400)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "본인 알림이 아니거나 미노출·취소·없는 알림 (code: 1006)")
    })
    @PatchMapping(value = "/{id}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ApiResponse<Void> read(@Parameter(hidden = true) @UserId Long userId,
            @Parameter(description = "읽음 처리할 알림 ID") @PathVariable Long id) {
        NotificationDispatch n = repository.findByIdAndUserId(id, userId)
                .filter(item -> item.getVisibleAt() != null && item.getStatus() != NotificationDispatch.Status.CANCELLED)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_ALREADY_DELETED));
        n.read(LocalDateTime.now(NotificationTiming.ZONE));
        return ApiResponse.success(null);
    }

    private Item item(NotificationDispatch n) {
        try {
            Map<String, String> data = objectMapper.readValue(n.getPayloadJson(), new TypeReference<>() { });
            return new Item(n.getId(), n.getTitle(), n.getBody(), data, n.getStatus(), n.getDeliveryReason(), n.getReadAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored notification payload is invalid", e);
        }
    }
}
