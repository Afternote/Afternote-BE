package com.afternote.domain.notification.engagement;

import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
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
@RequestMapping("/api/v1/users/me/notification-preferences")
@Tag(name = "Notification Preferences API", description = "데일리 알림 설정, 대리인, 기념일, 앱 복귀 문맥. 결제 제외.")
public class NotificationPreferencesController {
    private final EngagementNotificationService service;
    public record SettingsRequest(@NotNull Boolean enabled, @NotNull @Schema(type = "string", example = "18:00") LocalTime time,
            @NotEmpty Set<@NotNull DayOfWeek> weekdays, @Min(1) @Max(7) int weeklyGoal) { }
    public record SettingsResponse(boolean enabled, LocalTime time, Set<DayOfWeek> weekdays, int weeklyGoal) {
        static SettingsResponse from(NotificationProfile p) {
            var days = EnumSet.noneOf(DayOfWeek.class);
            for (DayOfWeek day : DayOfWeek.values()) if (p.selected(day)) days.add(day);
            return new SettingsResponse(p.isDailyEnabled(), p.getDailyTime(), days, p.getWeeklyGoal());
        }
    }
    public record DelegateRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 20) String phone) { }
    public record DelegateResponse(@Schema(nullable = true) String name, @Schema(nullable = true) String phone) { }
    public record AnniversaryRequest(@NotNull @Positive Long receiverId, @Min(1) @Max(12) int month, @Min(1) @Max(31) int day) { }
    @Operation(summary = "알림 시간·요일·주간 목표 조회", description = "기본 18:00, 모든 요일, 목표 주 3회. 기존 마음의 기록 푸시 OFF도 별도 적용됩니다.")
    @GetMapping public ApiResponse<SettingsResponse> get(@Parameter(hidden = true) @UserId Long id) {
        return ApiResponse.success(SettingsResponse.from(service.settings(id)));
    }
    @Operation(summary = "알림 시간·요일·주간 목표 변경", description = "한국 시간 HH:mm, 초는 0. 설정 변경 시 이전 대기 B 알림은 취소됩니다.")
    @PutMapping public ApiResponse<SettingsResponse> put(@Parameter(hidden = true) @UserId Long id, @Valid @RequestBody SettingsRequest r) {
        return ApiResponse.success(SettingsResponse.from(service.settings(id, r.enabled(), r.time(), r.weekdays(), r.weeklyGoal())));
    }
    @Operation(summary = "대리인 조회")
    @GetMapping("/delegate") public ApiResponse<DelegateResponse> delegate(@Parameter(hidden = true) @UserId Long id) {
        var p = service.settings(id); return ApiResponse.success(new DelegateResponse(p.getDelegateName(), p.getDelegatePhone()));
    }
    @Operation(summary = "대리인 지정·변경", description = "수신자와 별개의 연락 대상입니다. 변경하면 이전 대리인의 확인 링크는 무효가 됩니다.")
    @PutMapping("/delegate") public ApiResponse<Void> delegate(@Parameter(hidden = true) @UserId Long id, @Valid @RequestBody DelegateRequest r) {
        service.delegate(id, r.name(), r.phone()); return ApiResponse.success(null);
    }
    @Operation(summary = "대리인 해제")
    @DeleteMapping("/delegate") public ApiResponse<Void> removeDelegate(@Parameter(hidden = true) @UserId Long id) {
        service.delegate(id, null, null); return ApiResponse.success(null);
    }
    @Operation(summary = "기념일 목록")
    @GetMapping("/anniversaries") public ApiResponse<List<NotificationAnniversary>> anniversaries(@Parameter(hidden = true) @UserId Long id) {
        return ApiResponse.success(service.anniversaries(id));
    }
    @Operation(summary = "수신자 생일 등록", description = "존재하는 달력 날짜만 허용. 2월 29일은 평년에 2월 28일로 안내합니다.")
    @PostMapping("/anniversaries") public ApiResponse<NotificationAnniversary> anniversary(@Parameter(hidden = true) @UserId Long id, @Valid @RequestBody AnniversaryRequest r) {
        return ApiResponse.success(service.anniversary(id, r.receiverId(), r.month(), r.day()));
    }
    @Operation(summary = "기념일 삭제")
    @DeleteMapping("/anniversaries/{anniversaryId}") public ApiResponse<Void> removeAnniversary(@Parameter(hidden = true) @UserId Long id, @PathVariable Long anniversaryId) {
        service.deleteAnniversary(id, anniversaryId); return ApiResponse.success(null);
    }
    @Operation(summary = "앱 실행·복귀 문맥", description = "이전 방문을 기준으로 30일 복귀와 대리인 절차 취소 결과를 반환하고 활동 시각을 갱신합니다. 종료·로그인 후 앱 진입에서 호출합니다. 쉬운 질문은 작성 화면용 문구이며 daily question ID가 아닙니다.")
    @PostMapping("/entry") public ApiResponse<EngagementNotificationService.Entry> entry(@Parameter(hidden = true) @UserId Long id) {
        return ApiResponse.success(service.entry(id));
    }
}
