package com.afternote.domain.timeletter.controller;

import com.afternote.domain.timeletter.dto.request.TimeLetterCreateRequest;
import com.afternote.domain.timeletter.dto.request.TimeLetterDeleteRequest;
import com.afternote.domain.timeletter.dto.request.TimeLetterUpdateRequest;
import com.afternote.domain.timeletter.dto.response.TimeLetterListResponse;
import com.afternote.domain.timeletter.dto.response.TimeLetterResponse;
import com.afternote.domain.timeletter.service.TimeLetterService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "📬 TimeLetter API", description = "타임레터 관리 API")
@RestController
@RequestMapping("/api/v1/time-letters")
@RequiredArgsConstructor
public class TimeLetterController {

    private final TimeLetterService timeLetterService;

    @Operation(summary = "타임레터 전체 조회", description = "정식 등록된(SCHEDULED) 타임레터 전체를 조회합니다.")
    @GetMapping
    public ApiResponse<TimeLetterListResponse> getTimeLetters(
            @Parameter(hidden = true) @UserId Long userId) {
        return ApiResponse.success(timeLetterService.getTimeLetters(userId));
    }

    @Operation(summary = "타임레터 단일 조회", description = "특정 타임레터를 조회합니다.")
    @GetMapping("/{timeLetterId}")
    public ApiResponse<TimeLetterResponse> getTimeLetter(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long timeLetterId) {
        return ApiResponse.success(timeLetterService.getTimeLetter(userId, timeLetterId));
    }

    @Operation(
            summary = "타임레터 등록",
            description = "새 타임레터를 등록합니다. DRAFT는 수신자 선택 전에도 임시저장할 수 있고, SCHEDULED는 수신자가 최소 1명 필요합니다."
    )
    @PostMapping
    public ApiResponse<TimeLetterResponse> createTimeLetter(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody TimeLetterCreateRequest request) {
        return ApiResponse.success(timeLetterService.createTimeLetter(userId, request));
    }

    @Operation(summary = "임시저장 전체 조회", description = "임시저장된(DRAFT) 타임레터 전체를 조회합니다.")
    @GetMapping("/temporary")
    public ApiResponse<TimeLetterListResponse> getTemporaryTimeLetters(
            @Parameter(hidden = true) @UserId Long userId) {
        return ApiResponse.success(timeLetterService.getTemporaryTimeLetters(userId));
    }

    @Operation(summary = "타임레터 삭제", description = "타임레터를 단일/다건 삭제합니다.")
    @DeleteMapping
    public ApiResponse<Void> deleteTimeLetters(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody TimeLetterDeleteRequest request) {
        timeLetterService.deleteTimeLetters(userId, request);
        return ApiResponse.success(null);
    }

    @Operation(summary = "임시저장 전체 삭제", description = "임시저장된(DRAFT) 타임레터 전체를 삭제합니다.")
    @DeleteMapping("/temporary")
    public ApiResponse<Void> deleteAllTemporary(
            @Parameter(hidden = true) @UserId Long userId) {
        timeLetterService.deleteAllTemporary(userId);
        return ApiResponse.success(null);
    }

    @Operation(summary = "타임레터 수정", description = "타임레터를 수정합니다.")
    @PatchMapping("/{timeLetterId}")
    public ApiResponse<TimeLetterResponse> updateTimeLetter(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long timeLetterId,
            @Valid @RequestBody TimeLetterUpdateRequest request) {
        return ApiResponse.success(timeLetterService.updateTimeLetter(userId, timeLetterId, request));
    }
}
