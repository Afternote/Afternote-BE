package com.afternote.domain.auth.service;


import com.afternote.domain.auth.dto.*;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.auth.service.social.SocialLoginService;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.user.service.ActivityTouchService;
import com.afternote.domain.user.service.WithdrawalCooldownService;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import com.afternote.global.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    private final TokenService tokenService;
    private final EmailService emailService;
    private final WithdrawalCooldownService withdrawalCooldownService;
    
    // 🎯 핵심: SocialLoginFactory 주입
    private final SocialLoginFactory socialLoginFactory;
    private final ActivityTouchService activityTouchService;

    @Transactional
    public User signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        withdrawalCooldownService.assertNotInCooldown(request.getEmail());

        if (!emailService.consumeVerified(request.getEmail(), EmailVerificationPurpose.SIGNUP)) {
            throw new CustomException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();

        return saveNewUser(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 소셜 로그인 사용자는 일반 로그인 불가
        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_USER);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        activityTouchService.touch(user.getId());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        tokenService.saveToken(refreshToken, user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .build();
    }

    @Transactional
    public ReissueResponse reissue(ReissueRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. JWT 자체의 유효성 먼저 검증 (서명, 만료시간)
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. JWT에서 userId 추출
        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 3. Redis에서 원자적으로 조회 및 삭제 (RTR)
        Long storedUserId = tokenService.getUserIdAndDelete(refreshToken);
        if (storedUserId == null) {
            // 동시·직후 재발급: 승자가 남긴 같은 새 토큰 쌍 반환
            ReissueResponse grace = tokenService.findReissueGrace(refreshToken);
            if (grace != null) {
                return grace;
            }
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!storedUserId.equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 4. 로그인 상태 확정을 활동으로 집계 (클라이언트 ping 의존 제거)
        activityTouchService.touch(userId);

        // 5. 신규 토큰 발급 (RTR) + grace 캐시 (동시 패자용)
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        tokenService.saveToken(newRefreshToken, userId);

        ReissueResponse response = ReissueResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .build();
        tokenService.saveReissueGrace(refreshToken, response);
        return response;
    }

    @Transactional
    public void passwordChange(Long userId,PasswordChangeRequest request) {
        if(request.getNewPassword().equals(request.getCurrentPassword())){
            throw new CustomException(ErrorCode.NEWPASSWORD_MATCH);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        
        // 소셜 로그인 사용자는 비밀번호 변경 불가
        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_USER);
        }
        
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH); // 현재 비번 틀림 예외
        }
        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    @Transactional
    public void logout(Long userId, LogoutRequest request) {
        String refreshToken = request.getRefreshToken();
        
        // Redis에서 조회한 userId와 JWT의 userId가 일치하는지 확인
        Long storedUserId = tokenService.getUserId(refreshToken);
        if (storedUserId == null || !storedUserId.equals(userId)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        
        // Refresh Token 삭제 (이후 재발급 불가)
        tokenService.deleteToken(refreshToken);
    }

    @Transactional
    public EmailSendResponse emailSend(EmailSendRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
        withdrawalCooldownService.assertNotInCooldown(request.getEmail());
        return EmailSendResponse.of(
                emailService.sendCode(request.getEmail(), EmailVerificationPurpose.SIGNUP)
        );
    }

    @Transactional
    public void emailVerify(EmailVerifyRequest request) {
        if (!emailService.verifyAndDeleteCode(
                request.getEmail(),
                request.getCertificateCode(),
                EmailVerificationPurpose.SIGNUP
        )) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_VERIFICATION);
        }
        emailService.markVerified(request.getEmail(), EmailVerificationPurpose.SIGNUP);
    }

    @Transactional
    public EmailSendResponse findSendCode(FindSendCodeRequest request) {
        findActiveLocalUserForRecovery(request.getEmail());
        return EmailSendResponse.of(
                emailService.sendCode(request.getEmail(), EmailVerificationPurpose.FIND)
        );
    }

    @Transactional
    public EmailFindResponse findEmail(EmailFindRequest request) {
        User user = findActiveLocalUserForRecovery(request.getEmail());

        if (!emailService.verifyAndDeleteCode(
                request.getEmail(),
                request.getCertificateCode(),
                EmailVerificationPurpose.FIND
        )) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_VERIFICATION);
        }

        return EmailFindResponse.from(user.getName(), user.getEmail());
    }

    @Transactional
    public void findPassword(PasswordFindRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        User user = findActiveLocalUserForRecovery(request.getEmail());

        if (!emailService.verifyAndDeleteCode(
                request.getEmail(),
                request.getCertificateCode(),
                EmailVerificationPurpose.FIND
        )) {
            throw new CustomException(ErrorCode.INVALID_EMAIL_VERIFICATION);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.NEWPASSWORD_MATCH);
        }

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
    }

    private User findActiveUserForAccountRecovery(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_NOT_REGISTERED));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.EMAIL_NOT_REGISTERED);
        }

        return user;
    }

    private User findActiveLocalUserForRecovery(String email) {
        User user = findActiveUserForAccountRecovery(email);

        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_USER);
        }

        return user;
    }

    /**
     * 🎯 통합 소셜 로그인 (전략 패턴 적용)
     * 
     * 새로운 소셜 로그인 제공자를 추가할 때:
     * 1. SocialLoginService 인터페이스를 구현한 클래스 하나만 만들면 끝!
     * 2. 이 메서드는 1줄도 수정할 필요 없음 (OCP 원칙)
     * 
     * @param request 소셜 로그인 요청 (provider, accessToken)
     * @return JWT 토큰 정보와 신규 회원 여부
     */
    @Transactional
    public SocialLoginResponse socialLogin(SocialLoginRequest request) {

        SocialLoginService socialLoginService = socialLoginFactory.getService(request.getProvider());
        SocialUserInfo socialUserInfo = socialLoginService.getUserInfo(request.getAccessToken());

        User user = userRepository.findByEmail(socialUserInfo.getEmail())
                .orElse(null);
        boolean isNewUser = false;
        
        if (user == null) {
            withdrawalCooldownService.assertNotInCooldown(socialUserInfo.getEmail());
            user = User.builder()
                    .email(socialUserInfo.getEmail())
                    .name(socialUserInfo.getName())
                    .status(UserStatus.ACTIVE)
                    .provider(socialUserInfo.getProvider())
                    .build();
            user = saveNewUser(user);
            isNewUser = true;
        }
        
        // 5. JWT 토큰 생성
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());
        tokenService.saveToken(refreshToken, user.getId());
        
        return SocialLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationSeconds())
                .isNewUser(isNewUser)
                .build();
    }

    /**
     * existsByEmail 레이스에서 진 요청은 unique 제약으로 실패한다.
     * 500 대신 중복 이메일로 변환한다.
     */
    private User saveNewUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

}
