package com.afternote.domain.diary.controller;

import com.afternote.domain.diary.dto.DiaryHealthResponse;
import com.afternote.domain.diary.service.DiaryService;
import com.afternote.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "📝 Diary API", description = "다이어리 API")
@RestController
@RequestMapping("/api/v1/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "Diary MVC 레이어 상태 확인", description = "Diary 도메인의 MVC 스캐폴딩 상태를 확인합니다.")
    @GetMapping("/health")
    public ApiResponse<DiaryHealthResponse> getLayerStatus() {
        return ApiResponse.success(diaryService.getLayerStatus());
    }
}
