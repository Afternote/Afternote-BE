package com.afternote.domain.dailyquestion.controller;

import com.afternote.domain.dailyquestion.dto.DailyQuestionHealthResponse;
import com.afternote.domain.dailyquestion.service.DailyQuestionService;
import com.afternote.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "❓ DailyQuestion API", description = "매일 질문 API")
@RestController
@RequestMapping("/api/v1/daily-questions")
@RequiredArgsConstructor
public class DailyQuestionController {

    private final DailyQuestionService dailyQuestionService;

    @Operation(summary = "DailyQuestion MVC 레이어 상태 확인", description = "DailyQuestion 도메인의 MVC 스캐폴딩 상태를 확인합니다.")
    @GetMapping("/health")
    public ApiResponse<DailyQuestionHealthResponse> getLayerStatus() {
        return ApiResponse.success(dailyQuestionService.getLayerStatus());
    }
}
