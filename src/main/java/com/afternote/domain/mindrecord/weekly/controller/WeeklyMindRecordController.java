package com.afternote.domain.mindrecord.weekly.controller;

import com.afternote.domain.mindrecord.weekly.dto.WeeklyMindRecordResponse;
import com.afternote.domain.mindrecord.weekly.service.WeeklyMindRecordService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "🧠 Mind-Record", description = "마음 기록 (주간 리포트 등)")
@RestController
@RequestMapping("/api/v1/mind-record")
@RequiredArgsConstructor
public class WeeklyMindRecordController {

    private final WeeklyMindRecordService weeklyMindRecordService;

    @Operation(summary = "주간 리포트 조회", description = "해당 주의 월요일 날짜(yyyy-MM-dd)를 넘기면 그 주 월~일 기준으로 집계합니다.")
    @GetMapping
    public ApiResponse<WeeklyMindRecordResponse> getWeeklyReport(
            @Parameter(hidden = true) @UserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(weeklyMindRecordService.getWeeklyMindRecord(userId, date));
    }
}
