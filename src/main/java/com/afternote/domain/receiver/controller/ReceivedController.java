package com.afternote.domain.receiver.controller;

import com.afternote.domain.receiver.dto.*;
import com.afternote.domain.receiver.service.ReceivedService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Received API", description = "수신자 등록 API")
@RestController
@RequestMapping("/api/v1/received")
@RequiredArgsConstructor
public class ReceivedController {

    private final ReceivedService receivedService;

    @Operation(
            summary = "타임레터 수신자 등록",
            description = "타임레터에 수신자를 등록합니다. 반복·동시 요청은 기존 연결을 재사용하여 200과 연결 ID 목록을 반환합니다. 입력 ID의 첫 등장 순서를 유지하고 중복을 제거합니다. 기존 전달·읽음 상태는 변경하지 않습니다. DB 중복 충돌은 409(RECEIVER_ALREADY_REGISTERED)이며 재요청할 수 있습니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "기존 또는 신규 연결 ID 목록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "수신자 연결 중복 충돌 (code: 1909). 재요청 가능")
    @PostMapping("/time-letters")
    public ApiResponse<List<Long>> createTimeLetterReceivers(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody CreateTimeLetterReceiverRequest request
    ) {
        return ApiResponse.success(receivedService.createTimeLetterReceivers(userId, request));
    }

    @Operation(
            summary = "깊은 생각 수신자 등록",
            description = "깊은 생각에 수신자를 등록합니다. 여러 수신자를 한 번에 등록할 수 있습니다."
    )
    @PostMapping("/deep-thought")
    public ApiResponse<Void> createDeepThoughtReceivers(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody CreateDeepThoughtReceiverRequest request
    ) {
        receivedService.createDeepThoughtReceivers(userId, request);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "다이어리 수신자 등록",
            description = "다이어리에 수신자를 등록합니다. 여러 수신자를 한 번에 등록할 수 있습니다."
    )
    @PostMapping("/diary")
    public ApiResponse<Void> createDiaryReceivers(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody CreateDiaryReceiverRequest request
    ) {
        receivedService.createDiaryReceivers(userId, request);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "데일리 질문 수신자 등록",
            description = "데일리 질문 답변에 수신자를 등록합니다. 여러 수신자를 한 번에 등록할 수 있습니다."
    )
    @PostMapping("/daily-question")
    public ApiResponse<Void> createUserDailyQuestionReceivers(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody CreateUserDailyQuestionReceiverRequest request
    ) {
        receivedService.createUserDailyQuestionReceivers(userId, request);
        return ApiResponse.success(null);
    }
}
