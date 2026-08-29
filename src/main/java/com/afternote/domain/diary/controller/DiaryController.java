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

import java.time.YearMonth;

@Tag(name = "📝 Diary API", description = "다이어리 API")
@RestController
@RequestMapping("/api/v1/diary")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(
            summary = "Diary 작성",
            description = "새로운 다이어리를 작성합니다. 임시저장(isDraft=true)은 제목·본문·기분을 생략할 수 있습니다. "
                    + "정식 등록(isDraft=false)은 세 항목이 모두 필요합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diary 작성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값이 올바르지 않음 (code: 1400). 정식 등록 시 제목·본문·기분 누락 포함. 미래 기록일(code: 2101)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (code: 1000)")
    })
    @PostMapping
    public ApiResponse<DiaryResponse> createDiary(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody DiaryCreateRequest request
    ) {
        return ApiResponse.success(diaryService.createDiary(userId, request));
    }

    @Operation(summary = "Diary 월 단위 조회", description = "지정한 yyyy-MM 한 달의 다이어리를 기록일(date) 기준으로 조회합니다. "
            + "캘린더형 화면은 응답 date로 날짜별 그룹핑하고, 카드형 화면은 그대로 그리드에 렌더하면 됩니다. "
            + "draftOnly=true이면 해당 달의 임시저장만 반환합니다. "
            + "monthDiaryCount·weeklyDominantMood도 작성 시각이 아니라 기록일 기준입니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diary 조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값이 올바르지 않음 (code: 1400)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증되지 않은 요청 (code: 1000)")
    })
    @GetMapping
    public ApiResponse<DiaryListResponse> getDiaries(
            @Parameter(hidden = true) @UserId Long userId,
            @Parameter(description = "조회할 연-월 (yyyy-MM)", example = "2026-03")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
            @Parameter(description = "true면 임시저장(isDraft=true)만 조회")
            @RequestParam(required = false) Boolean draftOnly
    ) {
        return ApiResponse.success(diaryService.getDiariesByMonth(userId, yearMonth, draftOnly));
    }

    @Operation(
            summary = "Diary 수정",
            description = "다이어리를 수정합니다. 임시저장 유지는 제목·본문·기분을 생략할 수 있습니다. "
                    + "정식 등록(isDraft=false)으로 남기거나 전환하면 세 항목이 모두 필요합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Diary 수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 값이 올바르지 않음 (code: 1400). 정식 등록 시 제목·본문·기분 누락 포함. 미래 기록일(code: 2101)"
            ),
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
