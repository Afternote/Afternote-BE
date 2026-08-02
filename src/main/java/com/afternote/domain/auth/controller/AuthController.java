package com.afternote.domain.auth.controller;


import com.afternote.domain.auth.dto.*;
import com.afternote.domain.auth.service.AuthService;
import com.afternote.domain.user.model.User;
import com.afternote.global.common.ApiResponse;
import com.afternote.global.resolver.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@Tag(name = "🔐 auth API", description = "회원가입, 로그인 관련 API")
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;


    @Operation(summary = "회원가입 API", description = "회원가입을 합니다.")
    @PostMapping("/sign-up")
    public ApiResponse<SignupResponse> signUp(@Valid @RequestBody SignupRequest signupRequest) {
        // 회원가입 로직 구현
        User newuser = authService.signup(signupRequest);
        return ApiResponse.success(SignupResponse.from(newuser));
    }

    @Operation(summary = "로그인 API", description = "로그인을 합니다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        // 로그인 로직 구현
        LoginResponse loginResponse = authService.login(loginRequest);
        return ApiResponse.success(loginResponse);
    }

    @Operation(summary = "토큰 재발급 API", description = "리프레쉬 토큰을 이용해 다시 재발급을 합니다.")
    @PostMapping("/reissue")
    public ApiResponse<ReissueResponse> reissue(@Valid @RequestBody ReissueRequest reissueRequest) {

        ReissueResponse reissueResponse = authService.reissue(reissueRequest);
        return ApiResponse.success(reissueResponse);
    }

    @Operation(summary = "비밀번호 변경 API", description = "현재 비밀번호와 새 비밀번호를 입력합니다.")
    @PostMapping("/password/change")
    public ApiResponse<Object> passwordChange(
            @Parameter(hidden = true) @UserId Long userId, 
            @Valid @RequestBody PasswordChangeRequest passwordChangeRequest
    ) {
        authService.passwordChange(userId, passwordChangeRequest);
        return ApiResponse.success(null);
    }

    @Operation(summary = "로그아웃 API", description = "리프레쉬 토큰을 입력합니다.")
    @PostMapping("/logout")
    public ApiResponse<Object> logout(
            @Parameter(hidden = true) @UserId Long userId,
            @Valid @RequestBody  LogoutRequest logoutRequest
    ) {
        authService.logout(userId, logoutRequest);
        return ApiResponse.success(null);
    }

    @Operation(summary = "이메일 인증번호 전송 API", description = "이메일을 입력해 인증번호를 받습니다. 응답의 expiresAt으로 입력 화면 만료 카운트다운을 맞춥니다.")
    @PostMapping("/email/send")
    public ApiResponse<EmailSendResponse> emailSend(
            @Valid @RequestBody  EmailSendRequest emailSendRequest
    ) {
        return ApiResponse.success(authService.emailSend(emailSendRequest));
    }

    @Operation(summary = "이메일 인증번호 검증 API", description = "이메일과 인증코드를 통해 검증받습니다.")
    @PostMapping("/email/verify")
    public ApiResponse<Object> emailVerify(
            @Valid @RequestBody   EmailVerifyRequest emailVerifyRequest
    ) {
        authService.emailVerify(emailVerifyRequest);
        return ApiResponse.success(null);
    }

    @Operation(
            summary = "아이디/비밀번호 찾기 인증번호 발송 API",
            description = "로컬(이메일) 계정의 아이디/비밀번호 찾기용 인증번호를 발송합니다. 응답의 expiresAt으로 입력 화면 만료 카운트다운을 맞춥니다."
    )
    @PostMapping("/find/send/code")
    public ApiResponse<EmailSendResponse> findSendCode(
            @Valid @RequestBody FindSendCodeRequest findSendCodeRequest
    ) {
        return ApiResponse.success(authService.findSendCode(findSendCodeRequest));
    }

    @Operation(
            summary = "아이디(이메일) 찾기 API",
            description = "이메일과 인증번호를 검증한 뒤 아이디(이메일)를 반환합니다."
    )
    @PostMapping("/email/find")
    public ApiResponse<EmailFindResponse> findEmail(
            @Valid @RequestBody EmailFindRequest emailFindRequest
    ) {
        EmailFindResponse response = authService.findEmail(emailFindRequest);
        return ApiResponse.success(response);
    }

    @Operation(
            summary = "비밀번호 찾기 API",
            description = "이메일·인증번호·새 비밀번호를 검증한 뒤 비밀번호를 재설정합니다."
    )
    @PostMapping("/password/find")
    public ApiResponse<Object> findPassword(
            @Valid @RequestBody PasswordFindRequest passwordFindRequest
    ) {
        authService.findPassword(passwordFindRequest);
        return ApiResponse.success(null);
    }

    @Operation(
        summary = "통합 소셜 로그인 API 🎯", 
        description = """
            모든 소셜 로그인 제공자를 통합한 API입니다.
            
            **사용 방법:**
            - provider: KAKAO, GOOGLE, NAVER 등
            - accessToken: 소셜 로그인 제공자로부터 받은 Access Token
            
            **응답:**
            - accessToken: 서비스 JWT Access Token
            - refreshToken: 서비스 JWT Refresh Token
            - isNewUser: 신규 회원 여부 (true/false)
            
            **예시 (카카오):**
            ```json
            {
              "provider": "KAKAO",
              "accessToken": "카카오_액세스_토큰"
            }
            ```
            
            **예시 (구글):** 클라이언트(모바일)는 Google Sign-In SDK로 받은 **ID Token(JWT)** 을 `accessToken` 필드로 넘깁니다.
            서버는 ID Token의 서명 / iss / aud(=Web Client ID) / exp 를 검증합니다.
            Android/iOS 클라는 Google Sign-In의 `serverClientId`로 백엔드와 동일한 Web Client ID를 지정해야 합니다.
            ```json
            {
              "provider": "GOOGLE",
              "accessToken": "eyJhbGciOiJSUzI1NiIs..."
            }
            ```
            """
    )
    @PostMapping("/social/login")
    public ApiResponse<SocialLoginResponse> socialLogin(
            @Valid @RequestBody SocialLoginRequest socialLoginRequest
    ) {
        SocialLoginResponse response = authService.socialLogin(socialLoginRequest);
        return ApiResponse.success(response);
    }





}
