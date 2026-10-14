package com.afternote.domain.notification.policy;

import com.afternote.global.common.ApiResponse;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.resolver.UserId;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/notifications")
public class NotificationInboxController {
    private final NotificationDispatchRepository repository;
    private final ObjectMapper objectMapper;
    public record Item(Long id, String title, String body, Map<String, String> data,
            NotificationDispatch.Status status, String deliveryReason, LocalDateTime readAt) { }
    public record Inbox(long unreadCount, List<Item> items) { }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Inbox> list(@UserId Long userId, @RequestParam(defaultValue = "0") int page) {
        if (page < 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        return ApiResponse.success(new Inbox(repository.countByUserIdAndVisibleAtIsNotNullAndReadAtIsNullAndStatusNot(
                userId, NotificationDispatch.Status.CANCELLED),
                repository.findByUserIdAndVisibleAtIsNotNullAndStatusNotOrderByIdDesc(
                        userId, NotificationDispatch.Status.CANCELLED, PageRequest.of(page, 50))
                        .stream().map(this::item).toList()));
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ApiResponse<Void> read(@UserId Long userId, @PathVariable Long id) {
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
