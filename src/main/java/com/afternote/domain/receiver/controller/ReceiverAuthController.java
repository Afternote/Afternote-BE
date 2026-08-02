package com.afternote.domain.receiver.controller;

import com.afternote.domain.image.dto.PresignedUrlResponse;
import com.afternote.domain.receiver.model.ReceivedRecordSort;
import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.service.ReceiverAuthService;
import com.afternote.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@Tag(name = "Receiver Auth API", description = "수신자 인증번호 기반 콘텐츠 조회 API")
@RestController
@RequestMapping("/api/v1/receiver-auth")
@RequiredArgsConstructor
public class ReceiverAuthController {

    private final ReceiverAuthService receiverAuthService;

    @Operation(
            summary = "인증번호 검증",
            description = "수신자 인증번호를 검증하고 수신자/발신자 정보를 반환합니다."
    )
    @PostMapping("/verify")
    public ApiResponse<ReceiverAuthVerifyResponse> verifyAuthCode(
            @Valid @RequestBody ReceiverAuthVerifyRequest request
    ) {
        return ApiResponse.success(receiverAuthService.verifyAuthCode(request.getAuthCode()));
    }

    @Operation(
            summary = "인증번호로 타임레터 목록 조회",
            description = "인증번호를 통해 수신자에게 배달된 타임레터 목록을 조회합니다."
    )
    @GetMapping("/time-letters")
    public ApiResponse<ReceivedTimeLetterListResponse> getTimeLetters(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode
    ) {
        return ApiResponse.success(receiverAuthService.getTimeLettersByAuthCode(authCode));
    }

    @Operation(
            summary = "인증번호로 애프터노트 목록 조회",
            description = "인증번호를 통해 수신자에게 전달된 애프터노트 목록을 조회합니다."
    )
    @GetMapping("/after-notes")
    public ApiResponse<ReceivedAfternoteListResponse> getAfternotes(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode
    ) {
        return ApiResponse.success(receiverAuthService.getAfternotesByAuthCode(authCode));
    }

    @Operation(
            summary = "인증번호로 타임레터 상세 조회",
            description = "인증번호를 통해 수신한 특정 타임레터를 상세 조회합니다. 읽음 처리도 함께 수행됩니다."
    )
    @GetMapping("/time-letters/{timeLetterReceiverId}")
    public ApiResponse<ReceivedTimeLetterResponse> getTimeLetter(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Parameter(description = "수신 타임레터 ID", required = true)
            @PathVariable Long timeLetterReceiverId
    ) {
        return ApiResponse.success(receiverAuthService.getTimeLetterByAuthCode(authCode, timeLetterReceiverId));
    }

    @Operation(
            summary = "인증번호로 애프터노트 상세 조회",
            description = "인증번호를 통해 수신한 특정 애프터노트의 상세 내용을 조회합니다."
    )
    @GetMapping("/after-notes/{afternoteId}")
    public ApiResponse<ReceivedAfternoteDetailResponse> getAfternote(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Parameter(description = "애프터노트 ID", required = true)
            @PathVariable Long afternoteId
    ) {
        return ApiResponse.success(receiverAuthService.getAfternoteByAuthCode(authCode, afternoteId));
    }

    @Operation(
            summary = "수신자 일기 목록 조회",
            description = "수신자 접근 코드로 해당 수신자에게 전달된 일기 목록을 조회합니다."
    )
    @GetMapping("/diary")
    public ApiResponse<ReceivedDiaryListResponse> getReceivedDiaries(
            @Parameter(description = "수신자 접근 코드", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Parameter(description = "정렬 기준: LATEST 또는 OLDEST")
            @RequestParam(defaultValue = "LATEST") ReceivedRecordSort sort,
            @Parameter(description = "조회 시작일", example = "2026-12-12")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일", example = "2026-12-16")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(
                receiverAuthService.getReceivedDiaries(authCode, sort, startDate, endDate)
        );
    }

    @Operation(
            summary = "수신자 깊은 생각 목록 조회",
            description = "수신자 접근 코드로 해당 수신자에게 전달된 깊은 생각 목록을 조회합니다. 카테고리/태그/기간/정렬 필터를 지원합니다."
    )
    @GetMapping("/deep-thought")
    public ApiResponse<ReceivedDeepThoughtListResponse> getReceivedDeepThoughts(
            @Parameter(description = "수신자 접근 코드", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Parameter(description = "카테고리", example = "성장")
            @RequestParam(required = false) String category,
            @Parameter(description = "태그", example = "희망")
            @RequestParam(required = false) String tag,
            @Parameter(description = "정렬 기준: LATEST 또는 OLDEST")
            @RequestParam(defaultValue = "LATEST") ReceivedRecordSort sort,
            @Parameter(description = "조회 시작일", example = "2026-12-12")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일", example = "2026-12-16")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(
                receiverAuthService.getReceivedDeepThoughts(authCode, category, tag, sort, startDate, endDate)
        );
    }

    @Operation(
            summary = "수신자 데일리 질문 답변 목록 조회",
            description = "수신자 접근 코드로 해당 수신자에게 전달된 데일리 질문 답변 목록을 조회합니다."
    )
    @GetMapping("/daily-question")
    public ApiResponse<ReceivedDailyQuestionListResponse> getReceivedDailyQuestions(
            @Parameter(description = "수신자 접근 코드", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Parameter(description = "정렬 기준: LATEST 또는 OLDEST")
            @RequestParam(defaultValue = "LATEST") ReceivedRecordSort sort,
            @Parameter(description = "조회 시작일", example = "2026-12-12")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일", example = "2026-12-16")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(
                receiverAuthService.getReceivedDailyQuestions(authCode, sort, startDate, endDate)
        );
    }

    @Operation(
            summary = "발신자 메시지 조회",
            description = "인증번호를 통해 발신자가 남긴 메시지를 조회합니다."
    )
    @GetMapping("/message")
    public ApiResponse<ReceiverMessageResponse> getMessage(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode
    ) {
        return ApiResponse.success(receiverAuthService.getMessageByAuthCode(authCode));
    }

    @Operation(
            summary = "수신자 파일 업로드용 Presigned URL 생성",
            description = "수신자가 사망확인 서류(PDF, 이미지)를 S3에 업로드하기 위한 Presigned URL을 생성합니다."
    )
    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> getPresignedUrl(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Valid @RequestBody ReceiverPresignedUrlRequest request
    ) {
        return ApiResponse.success(receiverAuthService.generatePresignedUrl(authCode, request.getExtension()));
    }

    @Operation(
            summary = "사망확인 서류 제출",
            description = "수신자가 인증 서류를 제출합니다. 사망진단서와 가족관계증명서 중 하나 이상 제출하면 됩니다."
    )
    @PostMapping("/delivery-verification")
    public ApiResponse<DeliveryVerificationResponse> submitDeliveryVerification(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Valid @RequestBody DeliveryVerificationRequest request
    ) {
        return ApiResponse.success(receiverAuthService.submitDeliveryVerification(authCode, request));
    }

    @Operation(
            summary = "사망확인 인증 상태 조회",
            description = "수신자가 마지막으로 제출한 인증 요청 상태를 조회합니다."
    )
    @GetMapping("/delivery-verification/status")
    public ApiResponse<DeliveryVerificationResponse> getDeliveryVerificationStatus(
            @Parameter(description = "수신자 인증번호 (UUID)", required = true)
            @RequestHeader("X-Auth-Code") String authCode
    ) {
        return ApiResponse.success(receiverAuthService.getDeliveryVerificationStatus(authCode));
    }

    @Operation(
            summary = "수신자 이메일 인증번호 발송",
            description = "수신자 이메일로 6자리 인증번호를 발송합니다. 응답의 expiresAt으로 입력 화면 만료 카운트다운을 맞춥니다."
    )
    @PostMapping("/email/auth-code")
    public ApiResponse<ReceiverEmailAuthCodeSendResponse> sendEmailAuthCode(
            @Valid @RequestBody ReceiverAuthCodeEmailSendRequest request
    ) {
        return ApiResponse.success(receiverAuthService.sendEmailAuthCode(request.getEmail()));
    }

    @Operation(
            summary = "수신자 이메일 인증번호 검증",
            description = "수신자가 입력한 6자리 이메일 인증번호를 검증하고, 콘텐츠 조회용 접근 코드를 반환합니다."
    )
    @PostMapping("/email/verify")
    public ApiResponse<ReceiverEmailAuthVerifyResponse> verifyEmailAuthCode(
            @Valid @RequestBody ReceiverEmailAuthVerifyRequest request
    ) {
        return ApiResponse.success(
                receiverAuthService.verifyEmailAuthCode(
                        request.getEmail(),
                        request.getAuthCode()
                )
        );
    }

    @Operation(
            summary = "받은 기록함 리스트 조회",
            description = "수신자 접근 코드로 같은 이메일에 등록된 모든 받은 기록함 목록을 조회합니다."
    )
    @GetMapping("/record-boxes")
    public ApiResponse<ReceivedRecordBoxListResponse> getReceivedRecordBoxes(
            @Parameter(description = "수신자 접근 코드", required = true)
            @RequestHeader("X-Auth-Code") String authCode
    ) {
        return ApiResponse.success(receiverAuthService.getReceivedRecordBoxes(authCode));
    }

    @Operation(
            summary = "받은 기록함 단일 조회",
            description = "수신자 접근 코드로 같은 이메일에 등록된 특정 받은 기록함을 조회합니다."
    )
    @GetMapping("/record-boxes/{receiverId}")
    public ApiResponse<ReceivedRecordBoxResponse> getReceivedRecordBox(
            @Parameter(description = "수신자 접근 코드", required = true)
            @RequestHeader("X-Auth-Code") String authCode,
            @Parameter(description = "조회할 수신자 ID", required = true)
            @PathVariable Long receiverId
    ) {
        return ApiResponse.success(receiverAuthService.getReceivedRecordBox(authCode, receiverId));
    }
}
