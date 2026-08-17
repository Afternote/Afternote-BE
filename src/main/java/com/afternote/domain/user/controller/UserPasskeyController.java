package com.afternote.domain.user.controller;

import com.afternote.domain.auth.dto.PasskeyResponse;
import com.afternote.domain.auth.service.PasskeyService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "User API", description = "회원 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/passkeys")
public class UserPasskeyController {

    private final PasskeyService passkeyService;

    @Operation(summary = "패스키 목록", description = "로그인한 계정에 등록된 패스키 목록입니다.")
    @GetMapping
    public ApiResponse<List<PasskeyResponse>> list(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(passkeyService.list(userId));
    }

    @Operation(summary = "패스키 삭제", description = "본인 패스키만 삭제합니다. 마지막 로그인 수단이면 400/1213.")
    @DeleteMapping("/{passkeyId}")
    public ApiResponse<Void> delete(
            @Parameter(hidden = true) @UserId Long userId,
            @PathVariable Long passkeyId
    ) {
        passkeyService.delete(userId, passkeyId);
        return ApiResponse.success(null);
    }
}
