package com.afternote.domain.dailyquestion.controller;

import com.afternote.domain.dailyquestion.dto.*;
import com.afternote.domain.dailyquestion.service.DailyQuestionService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "❓ DailyQuestion API", description = "매일 질문 API")
@RestController
@RequestMapping("/api/v1/daily-questions")
@RequiredArgsConstructor
public class DailyQuestionController {

    private final DailyQuestionService dailyQuestionService;

    @Operation(summary = "오늘의 데일리 질문 조회", description = "서버 시간 기준으로 오늘 할당된 질문을 가져옵니다. 없으면 새로 할당합니다.")
    @GetMapping("/today")
    public ApiResponse<DailyQuestionTodayResponse> getTodayQuestion(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(dailyQuestionService.getTodayQuestion(userId));
    }

    @Operation(summary = "데일리 질문 답변 작성", description = "데일리 질문에 대한 답변을 작성합니다.")
    @PostMapping
    public ApiResponse<DailyQuestionAnswerResponse> createAnswer(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody DailyQuestionAnswerRequest request
    ) {
        return ApiResponse.success(dailyQuestionService.createAnswer(userId, request));
    }

    @Operation(summary = "데일리 질문 수정", description = "특정 답변 ID를 기준으로 내용을 수정합니다.")
    @PatchMapping("/{userDailyQuestionId}")
    public ApiResponse<DailyQuestionAnswerResponse> updateAnswer(
            @Parameter(hidden = true) @UserId Long userId,
            @Parameter(description = "유저 데일리 질문(답변) ID", example = "10") @PathVariable Long userDailyQuestionId,
            @Valid @RequestBody DailyQuestionUpdateRequest request
    ) {
        return ApiResponse.success(dailyQuestionService.updateAnswer(userId, userDailyQuestionId, request));
    }

    @Operation(summary = "데일리 질문 목록 조회", description = "특정 날짜 혹은 전체 답변 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ApiResponse<List<DailyQuestionListResponse>> getDailyQuestions(
            @Parameter(hidden = true) @UserId Long userId,
            @Parameter(description = "조회할 날짜 (yyyy-MM-dd), 없으면 전체 조회", example = "2023-10-01")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date
    ) {
        return ApiResponse.success(dailyQuestionService.getDailyQuestions(userId, date));
    }

    @Operation(summary = "데일리 질문 삭제", description = "유저의 답변을 삭제합니다.")
    @DeleteMapping("/{userDailyQuestionId}")
    public ApiResponse<Void> deleteAnswer(
            @Parameter(hidden = true) @UserId Long userId,
            @Parameter(description = "유저 데일리 질문(답변) ID", example = "10") @PathVariable Long userDailyQuestionId
    ) {
        dailyQuestionService.deleteAnswer(userId, userDailyQuestionId);
        return ApiResponse.success(null);
    }
}
