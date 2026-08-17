package com.afternote.domain.auth.controller;

import com.afternote.domain.auth.dto.LoginResponse;
import com.afternote.domain.auth.dto.PasskeyCreationOptionsResponse;
import com.afternote.domain.auth.dto.PasskeyRequestOptionsResponse;
import com.afternote.domain.auth.dto.PasskeyResponse;
import com.afternote.domain.auth.service.PasskeyService;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "🔐 auth API", description = "패스키(WebAuthn) 등록·로그인")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/passkey")
public class PasskeyAuthController {

    private final PasskeyService passkeyService;

    @Operation(
            summary = "패스키 등록 옵션",
            description = "로그인 상태에서 PublicKeyCredentialCreationOptions 를 내려줍니다. 앱은 Credential Manager createCredential()에 넣습니다."
    )
    @PostMapping("/register/options")
    public ApiResponse<PasskeyCreationOptionsResponse> registerOptions(
            @Parameter(hidden = true) @UserId Long userId
    ) {
        return ApiResponse.success(passkeyService.registerOptions(userId));
    }

    @Operation(
            summary = "패스키 등록 검증",
            description = "createCredential() 응답(PublicKeyCredential JSON)을 검증하고 공개키를 저장합니다. displayName 은 선택."
    )
    @PostMapping("/register")
    public ApiResponse<PasskeyResponse> register(
            @Parameter(hidden = true) @UserId Long userId,
            @RequestBody JsonNode body
    ) {
        return ApiResponse.success(passkeyService.register(userId, body));
    }

    @Operation(
            summary = "패스키 인증 옵션",
            description = "비로그인. usernameless PublicKeyCredentialRequestOptions. allowCredentials 는 비웁니다."
    )
    @PostMapping("/authenticate/options")
    public ApiResponse<PasskeyRequestOptionsResponse> authenticateOptions() {
        return ApiResponse.success(passkeyService.authenticateOptions());
    }

    @Operation(
            summary = "패스키 인증 검증",
            description = "getCredential() assertion 을 검증하고 기존 로그인과 같은 토큰 봉투를 발급합니다."
    )
    @PostMapping("/authenticate")
    public ApiResponse<LoginResponse> authenticate(@RequestBody JsonNode body) {
        return ApiResponse.success(passkeyService.authenticate(body));
    }
}
