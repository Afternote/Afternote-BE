package com.afternote.domain.auth.service;

import com.afternote.domain.auth.dto.*;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.auth.service.social.SocialLoginService;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.user.service.WithdrawalCooldownService;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenService tokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private WithdrawalCooldownService withdrawalCooldownService;

    @Mock
    private SocialLoginFactory socialLoginFactory;

    @Mock
    private SocialLoginService socialLoginService;

    @Test
    @DisplayName("회원가입 실패 - 이메일 중복")
    void signup_DuplicateEmail_Fail() {
        SignupRequest request = org.mockito.Mockito.mock(SignupRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(userRepository.existsByEmail("test@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));
    }

    @Test
    @DisplayName("회원가입 실패 - 이메일 미인증")
    void signup_EmailNotVerified_Fail() {
        SignupRequest request = org.mockito.Mockito.mock(SignupRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(emailService.consumeVerified("test@test.com", EmailVerificationPurpose.SIGNUP)).willReturn(false);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED));
    }

    @Test
    @DisplayName("이메일 인증번호 발송 실패 - 탈퇴 후 30일 쿨다운")
    void emailSend_WithdrawalCooldown_Fail() {
        EmailSendRequest request = org.mockito.Mockito.mock(EmailSendRequest.class);
        given(request.getEmail()).willReturn("gone@test.com");
        given(userRepository.existsByEmail("gone@test.com")).willReturn(false);
        willThrow(new CustomException(ErrorCode.WITHDRAWAL_COOLDOWN))
                .given(withdrawalCooldownService).assertNotInCooldown("gone@test.com");

        assertThatThrownBy(() -> authService.emailSend(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.WITHDRAWAL_COOLDOWN));
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_Success() {
        SignupRequest request = org.mockito.Mockito.mock(SignupRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(request.getPassword()).willReturn("Password1!");
        given(request.getName()).willReturn("tester");

        given(userRepository.existsByEmail("test@test.com")).willReturn(false);
        given(emailService.consumeVerified("test@test.com", EmailVerificationPurpose.SIGNUP)).willReturn(true);
        given(passwordEncoder.encode("Password1!")).willReturn("encoded");
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User u = invocation.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });

        User user = authService.signup(request);

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getEmail()).isEqualTo("test@test.com");
        verify(emailService).consumeVerified("test@test.com", EmailVerificationPurpose.SIGNUP);
        verify(tokenService, org.mockito.Mockito.never()).saveToken(any(), any());
    }

    @Test
    @DisplayName("로그인 실패 - 소셜 로그인 사용자")
    void login_SocialUser_Fail() {
        LoginRequest request = org.mockito.Mockito.mock(LoginRequest.class);
        given(request.getEmail()).willReturn("social@test.com");

        User socialUser = User.builder()
                .email("social@test.com")
                .password(null)
                .name("social")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.KAKAO)
                .build();

        given(userRepository.findByEmail("social@test.com")).willReturn(Optional.of(socialUser));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.SOCIAL_CREDENTIALS_REQUIRED));
    }

    @Test
    @DisplayName("로그인 성공")
    void login_Success() {
        LoginRequest request = org.mockito.Mockito.mock(LoginRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(request.getPassword()).willReturn("Password1!");

        User user = User.builder()
                .email("test@test.com")
                .password("encoded")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 10L);

        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Password1!", "encoded")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(10L)).willReturn("access");
        given(jwtTokenProvider.generateRefreshToken(10L)).willReturn("refresh");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);

        LoginResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        verify(tokenService).saveToken("refresh", 10L);
    }

    @Test
    @DisplayName("재발급 실패 - 유효하지 않은 리프레시 토큰")
    void reissue_InvalidToken_Fail() {
        ReissueRequest request = org.mockito.Mockito.mock(ReissueRequest.class);
        given(request.getRefreshToken()).willReturn("rt");
        given(jwtTokenProvider.validateToken("rt")).willReturn(false);

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("재발급 성공")
    void reissue_Success() {
        ReissueRequest request = org.mockito.Mockito.mock(ReissueRequest.class);
        given(request.getRefreshToken()).willReturn("oldRt");

        given(jwtTokenProvider.validateToken("oldRt")).willReturn(true);
        given(jwtTokenProvider.getUserId("oldRt")).willReturn(11L);
        given(tokenService.getUserIdAndDelete("oldRt")).willReturn(11L);
        given(jwtTokenProvider.generateAccessToken(11L)).willReturn("newAccess");
        given(jwtTokenProvider.generateRefreshToken(11L)).willReturn("newRt");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);

        ReissueResponse response = authService.reissue(request);

        assertThat(response.getAccessToken()).isEqualTo("newAccess");
        assertThat(response.getRefreshToken()).isEqualTo("newRt");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        verify(tokenService).saveToken("newRt", 11L);
    }

    @Test
    @DisplayName("비밀번호 변경 실패 - 새 비밀번호 동일")
    void passwordChange_SamePassword_Fail() {
        PasswordChangeRequest request = org.mockito.Mockito.mock(PasswordChangeRequest.class);
        given(request.getCurrentPassword()).willReturn("Password1!");
        given(request.getNewPassword()).willReturn("Password1!");

        assertThatThrownBy(() -> authService.passwordChange(1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.NEWPASSWORD_MATCH));
    }

    @Test
    @DisplayName("로그아웃 실패 - 토큰 소유자 불일치")
    void logout_InvalidTokenOwner_Fail() {
        LogoutRequest request = org.mockito.Mockito.mock(LogoutRequest.class);
        given(request.getRefreshToken()).willReturn("rt");
        given(tokenService.getUserId("rt")).willReturn(999L);

        assertThatThrownBy(() -> authService.logout(1L, request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("이메일 인증번호 전송 실패 - 이미 가입된 이메일")
    void emailSend_DuplicateEmail_Fail() {
        EmailSendRequest request = org.mockito.Mockito.mock(EmailSendRequest.class);
        given(request.getEmail()).willReturn("dup@test.com");
        given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

        assertThatThrownBy(() -> authService.emailSend(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL));

        verify(emailService, org.mockito.Mockito.never()).sendCode(any(), any());
    }

    @Test
    @DisplayName("이메일 인증번호 전송 성공")
    void emailSend_Success() {
        EmailSendRequest request = org.mockito.Mockito.mock(EmailSendRequest.class);
        given(request.getEmail()).willReturn("new@test.com");
        given(userRepository.existsByEmail("new@test.com")).willReturn(false);
        java.time.Instant expiresAt = java.time.Instant.parse("2026-07-06T13:45:30Z");
        given(emailService.sendCode("new@test.com", EmailVerificationPurpose.SIGNUP)).willReturn(expiresAt);

        EmailSendResponse response = authService.emailSend(request);

        assertThat(response.getExpiresAt()).isEqualTo(expiresAt);
        verify(emailService).sendCode("new@test.com", EmailVerificationPurpose.SIGNUP);
    }

    @Test
    @DisplayName("이메일 인증 실패")
    void emailVerify_Fail() {
        EmailVerifyRequest request = org.mockito.Mockito.mock(EmailVerifyRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(request.getCertificateCode()).willReturn("111111");
        given(emailService.verifyAndDeleteCode("test@test.com", "111111", EmailVerificationPurpose.SIGNUP)).willReturn(false);

        assertThatThrownBy(() -> authService.emailVerify(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.INVALID_EMAIL_VERIFICATION));
    }

    @Test
    @DisplayName("이메일 인증 성공 - 가입용 인증 완료 플래그 저장")
    void emailVerify_Success() {
        EmailVerifyRequest request = org.mockito.Mockito.mock(EmailVerifyRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(request.getCertificateCode()).willReturn("123456");
        given(emailService.verifyAndDeleteCode("test@test.com", "123456", EmailVerificationPurpose.SIGNUP)).willReturn(true);

        authService.emailVerify(request);

        verify(emailService).markVerified("test@test.com", EmailVerificationPurpose.SIGNUP);
    }

    @Test
    @DisplayName("소셜 로그인 성공 - 신규 유저 생성")
    void socialLogin_NewUser_Success() {
        SocialLoginRequest request = org.mockito.Mockito.mock(SocialLoginRequest.class);
        given(request.getProvider()).willReturn("KAKAO");
        given(request.getAccessToken()).willReturn("kakao-token");

        SocialUserInfo socialUserInfo = SocialUserInfo.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .email("kakao@test.com")
                .name("kakao")
                .build();

        given(socialLoginFactory.getService("KAKAO")).willReturn(socialLoginService);
        given(socialLoginService.getUserInfo("kakao-token")).willReturn(socialUserInfo);
        given(userRepository.findByEmail("kakao@test.com")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 77L);
            return saved;
        });
        given(jwtTokenProvider.generateAccessToken(77L)).willReturn("social-access");
        given(jwtTokenProvider.generateRefreshToken(77L)).willReturn("social-refresh");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);

        SocialLoginResponse response = authService.socialLogin(request);

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.getAccessToken()).isEqualTo("social-access");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        verify(tokenService).saveToken("social-refresh", 77L);
    }

    @Test
    @DisplayName("소셜 로그인 성공 - 기존 유저 재로그인")
    void socialLogin_ExistingUser_Success() {
        SocialLoginRequest request = org.mockito.Mockito.mock(SocialLoginRequest.class);
        given(request.getProvider()).willReturn("KAKAO");
        given(request.getAccessToken()).willReturn("kakao-token");

        SocialUserInfo socialUserInfo = SocialUserInfo.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .email("existing@test.com")
                .name("existing")
                .build();

        User existingUser = User.builder()
                .email("existing@test.com")
                .password(null)
                .name("existing")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.KAKAO)
                .build();
        ReflectionTestUtils.setField(existingUser, "id", 88L);

        given(socialLoginFactory.getService("KAKAO")).willReturn(socialLoginService);
        given(socialLoginService.getUserInfo("kakao-token")).willReturn(socialUserInfo);
        given(userRepository.findByEmail("existing@test.com")).willReturn(Optional.of(existingUser));
        given(jwtTokenProvider.generateAccessToken(88L)).willReturn("existing-access");
        given(jwtTokenProvider.generateRefreshToken(88L)).willReturn("existing-refresh");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);

        SocialLoginResponse response = authService.socialLogin(request);

        assertThat(response.isNewUser()).isFalse();
        assertThat(response.getAccessToken()).isEqualTo("existing-access");
        assertThat(response.getRefreshToken()).isEqualTo("existing-refresh");
        assertThat(response.getExpiresIn()).isEqualTo(3600L);
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
        verify(tokenService).saveToken("existing-refresh", 88L);
    }

    @Test
    @DisplayName("아이디/비밀번호 찾기 인증번호 발송 성공")
    void findSendCode_Success() {
        FindSendCodeRequest request = org.mockito.Mockito.mock(FindSendCodeRequest.class);
        given(request.getEmail()).willReturn("test@test.com");

        User user = User.builder()
                .email("test@test.com")
                .password("encoded")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        java.time.Instant expiresAt = java.time.Instant.parse("2026-07-06T13:45:30Z");
        given(emailService.sendCode("test@test.com", EmailVerificationPurpose.FIND)).willReturn(expiresAt);

        EmailSendResponse response = authService.findSendCode(request);

        assertThat(response.getExpiresAt()).isEqualTo(expiresAt);
        verify(emailService).sendCode("test@test.com", EmailVerificationPurpose.FIND);
    }

    @Test
    @DisplayName("아이디 찾기 - 인증 성공")
    void findEmail_Verify_Success() {
        EmailFindRequest request = org.mockito.Mockito.mock(EmailFindRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(request.getCertificateCode()).willReturn("123456");

        User user = User.builder()
                .email("test@test.com")
                .password("encoded")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(emailService.verifyAndDeleteCode("test@test.com", "123456", EmailVerificationPurpose.FIND))
                .willReturn(true);

        EmailFindResponse response = authService.findEmail(request);

        assertThat(response.getName()).isEqualTo("tester");
        assertThat(response.getEmail()).isEqualTo("test@test.com");
    }

    @Test
    @DisplayName("아이디 찾기 실패 - 가입되지 않은 이메일")
    void findEmail_NotRegistered_Fail() {
        EmailFindRequest request = org.mockito.Mockito.mock(EmailFindRequest.class);
        given(request.getEmail()).willReturn("unknown@test.com");
        given(userRepository.findByEmail("unknown@test.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.findEmail(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_REGISTERED));
    }

    @Test
    @DisplayName("비밀번호 찾기 - 재설정 성공")
    void findPassword_Reset_Success() {
        PasswordFindRequest request = org.mockito.Mockito.mock(PasswordFindRequest.class);
        given(request.getEmail()).willReturn("test@test.com");
        given(request.getCertificateCode()).willReturn("123456");
        given(request.getNewPassword()).willReturn("NewPass1!");
        given(request.getConfirmPassword()).willReturn("NewPass1!");

        User user = User.builder()
                .email("test@test.com")
                .password("encoded")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        given(userRepository.findByEmail("test@test.com")).willReturn(Optional.of(user));
        given(emailService.verifyAndDeleteCode("test@test.com", "123456", EmailVerificationPurpose.FIND))
                .willReturn(true);
        given(passwordEncoder.matches("NewPass1!", "encoded")).willReturn(false);
        given(passwordEncoder.encode("NewPass1!")).willReturn("new-encoded");

        authService.findPassword(request);

        assertThat(user.getPassword()).isEqualTo("new-encoded");
    }

    @Test
    @DisplayName("아이디/비밀번호 찾기 실패 - 소셜 계정")
    void findSendCode_SocialUser_Fail() {
        FindSendCodeRequest request = org.mockito.Mockito.mock(FindSendCodeRequest.class);
        given(request.getEmail()).willReturn("social@test.com");

        User socialUser = User.builder()
                .email("social@test.com")
                .password(null)
                .name("social")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.KAKAO)
                .build();

        given(userRepository.findByEmail("social@test.com")).willReturn(Optional.of(socialUser));

        assertThatThrownBy(() -> authService.findSendCode(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.SOCIAL_LOGIN_USER));
    }

    @Test
    @DisplayName("아이디 찾기 실패 - 소셜 계정")
    void findEmail_SocialUser_Fail() {
        EmailFindRequest request = org.mockito.Mockito.mock(EmailFindRequest.class);
        given(request.getEmail()).willReturn("social@test.com");

        User socialUser = User.builder()
                .email("social@test.com")
                .password(null)
                .name("social")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.KAKAO)
                .build();

        given(userRepository.findByEmail("social@test.com")).willReturn(Optional.of(socialUser));

        assertThatThrownBy(() -> authService.findEmail(request))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.SOCIAL_LOGIN_USER));
    }
}
