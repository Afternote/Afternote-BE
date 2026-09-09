package com.afternote.domain.receiver.controller;

import com.afternote.domain.image.dto.PresignedUrlResponse;
import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.model.ReceivedRecordSort;
import com.afternote.domain.receiver.service.ReceivedRecordService;
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

@Tag(name = "Received Record API", description = "로그인한 수신자의 받은 기록 조회 API")
@RestController
@RequestMapping("/api/v1/received-records")
@RequiredArgsConstructor
public class ReceivedRecordController {

    private final ReceivedRecordService receivedRecordService;

    @Operation(summary = "받은 기록함 목록 조회")
    @GetMapping
    public ApiResponse<ReceivedRecordBoxListResponse> getRecordBoxes(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(receivedRecordService.getRecordBoxes(userId));
    }

    @Operation(summary = "받은 기록함 단일 조회")
    @GetMapping("/{receiverId}")
    public ApiResponse<ReceivedRecordBoxResponse> getRecordBox(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(receivedRecordService.getRecordBox(userId, receiverId));
    }

    @Operation(summary = "받은 타임레터 목록 조회")
    @GetMapping("/{receiverId}/time-letters")
    public ApiResponse<ReceivedTimeLetterListResponse> getTimeLetters(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(receivedRecordService.getTimeLetters(userId, receiverId));
    }

    @Operation(summary = "받은 타임레터 상세 조회")
    @GetMapping("/{receiverId}/time-letters/{timeLetterReceiverId}")
    public ApiResponse<ReceivedTimeLetterResponse> getTimeLetter(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @PathVariable Long timeLetterReceiverId
    ) {
        return ApiResponse.success(
                receivedRecordService.getTimeLetter(userId, receiverId, timeLetterReceiverId));
    }

    @Operation(summary = "받은 애프터노트 목록 조회")
    @GetMapping("/{receiverId}/after-notes")
    public ApiResponse<ReceivedAfternoteListResponse> getAfternotes(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(receivedRecordService.getAfternotes(userId, receiverId));
    }

    @Operation(summary = "받은 애프터노트 상세 조회")
    @GetMapping("/{receiverId}/after-notes/{afternoteId}")
    public ApiResponse<ReceivedAfternoteDetailResponse> getAfternote(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @PathVariable Long afternoteId
    ) {
        return ApiResponse.success(receivedRecordService.getAfternote(userId, receiverId, afternoteId));
    }

    @Operation(summary = "받은 일기 목록 조회")
    @GetMapping("/{receiverId}/diary")
    public ApiResponse<ReceivedDiaryListResponse> getDiaries(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @RequestParam(defaultValue = "LATEST") ReceivedRecordSort sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(
                receivedRecordService.getDiaries(userId, receiverId, sort, startDate, endDate));
    }

    @Operation(summary = "받은 깊은 생각 목록 조회")
    @GetMapping("/{receiverId}/deep-thought")
    public ApiResponse<ReceivedDeepThoughtListResponse> getDeepThoughts(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "LATEST") ReceivedRecordSort sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(receivedRecordService.getDeepThoughts(
                userId, receiverId, category, tag, sort, startDate, endDate));
    }

    @Operation(summary = "받은 데일리 질문 답변 목록 조회")
    @GetMapping("/{receiverId}/daily-question")
    public ApiResponse<ReceivedDailyQuestionListResponse> getDailyQuestions(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @RequestParam(defaultValue = "LATEST") ReceivedRecordSort sort,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(
                receivedRecordService.getDailyQuestions(userId, receiverId, sort, startDate, endDate));
    }

    @Operation(summary = "발신자가 남긴 메시지 조회")
    @GetMapping("/{receiverId}/message")
    public ApiResponse<ReceiverMessageResponse> getMessage(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(receivedRecordService.getMessage(userId, receiverId));
    }

    @Operation(summary = "사망확인 서류 업로드용 Presigned URL 생성")
    @PostMapping("/{receiverId}/presigned-url")
    public ApiResponse<PresignedUrlResponse> getPresignedUrl(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @Valid @RequestBody ReceiverPresignedUrlRequest request
    ) {
        return ApiResponse.success(receivedRecordService.generatePresignedUrl(
                userId, receiverId, request.getExtension(), request.getContentLength()));
    }

    @Operation(summary = "사망확인 서류 제출")
    @PostMapping("/{receiverId}/delivery-verification")
    public ApiResponse<DeliveryVerificationResponse> submitDeliveryVerification(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId,
            @Valid @RequestBody DeliveryVerificationRequest request
    ) {
        return ApiResponse.success(
                receivedRecordService.submitDeliveryVerification(userId, receiverId, request));
    }

    @Operation(summary = "사망확인 인증 상태 조회")
    @GetMapping("/{receiverId}/delivery-verification/status")
    public ApiResponse<DeliveryVerificationResponse> getDeliveryVerificationStatus(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(
                receivedRecordService.getDeliveryVerificationStatus(userId, receiverId));
    }
}
