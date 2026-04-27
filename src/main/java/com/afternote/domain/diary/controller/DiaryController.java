package com.afternote.domain.diary.controller;

import com.afternote.domain.diary.dto.DiaryCreateRequest;
import com.afternote.domain.diary.dto.DiaryListResponse;
import com.afternote.domain.diary.dto.DiaryResponse;
import com.afternote.domain.diary.dto.DiaryUpdateRequest;
import com.afternote.domain.diary.service.DiaryService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Tag(name = "📝 Diary API", description = "다이어리 API")
@RestController
@RequestMapping("/api/v1/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(summary = "Diary 작성", description = "새로운 다이어리를 작성합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diary 작성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음 (code: 1400)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (code: 1000)")
    })
    @PostMapping
    public ApiResponse<DiaryResponse> createDiary(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody DiaryCreateRequest request
    ) {
        return ApiResponse.success(diaryService.createDiary(userId, request));
    }

    @Operation(summary = "Diary 조회", description = "날짜 기준으로 다이어리를 조회합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diary 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음 (code: 1400)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (code: 1000)")
    })
    @GetMapping
    public ApiResponse<DiaryListResponse> getDiaries(
            @Parameter(hidden = true) @UserId Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.success(diaryService.getDiariesByDate(userId, date));
    }

    @Operation(summary = "Diary 수정", description = "다이어리를 수정합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diary 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음 (code: 1400)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (code: 1000)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "다이어리를 찾을 수 없음 (code: 2100)")
    })
    @PatchMapping("/{diaryId}")
    public ApiResponse<DiaryResponse> updateDiary(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long diaryId,
            @RequestBody DiaryUpdateRequest request
    ) {
        return ApiResponse.success(diaryService.updateDiary(userId, diaryId, request));
    }

    @Operation(summary = "Diary 삭제", description = "다이어리를 삭제합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diary 삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (code: 1000)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "다이어리를 찾을 수 없음 (code: 2100)")
    })
    @DeleteMapping("/{diaryId}")
    public ApiResponse<Void> deleteDiary(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long diaryId
    ) {
        diaryService.deleteDiary(userId, diaryId);
        return ApiResponse.success(null);
    }
}
