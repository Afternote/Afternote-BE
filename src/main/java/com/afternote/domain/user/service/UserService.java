package com.afternote.domain.user.service;

import com.afternote.domain.auth.dto.SocialUserInfo;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.UserReceiver;
import com.afternote.domain.receiver.service.DeliveryVerificationService;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.user.dto.*;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserProvider;
import com.afternote.domain.user.repository.UserProviderRepository;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.receiver.service.AuthCodeMessageService;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final UserReceiverRepository userReceiverRepository;
    private final ReceiverRepository receiverRepository;
    private final AuthCodeMessageService authCodeMessageService;
    private final S3Service s3Service;
    private final DeliveryVerificationService deliveryVerificationService;
    private final com.afternote.domain.auth.service.TokenService tokenService;
    private final SocialLoginFactory socialLoginFactory;
    private final UserProviderRepository userProviderRepository;

    public UserResponse getMyProfile(Long userId) {

        User user = findUserById(userId);
        return UserResponse.from(user, s3Service::generateGetPresignedUrl);
    }

    @Transactional
    public UserResponse updateMyProfile(Long userId, UserUpdateProfileRequest request) {

        User user = findUserById(userId);

        String profileImageKey = request.getProfileImageUrl();
        if (profileImageKey != null && !profileImageKey.isBlank()) {
            String extracted = s3Service.extractStorageKey(profileImageKey);
            profileImageKey = extracted != null ? extracted : profileImageKey;
        }

        user.updateProfile(
                request.getName(),
                request.getPhone(),
                profileImageKey
        );

        return UserResponse.from(user, s3Service::generateGetPresignedUrl);
    }


    public UserPushSettingResponse getMyPushSettings(Long userId) {
        User user = findUserById(userId);

        return UserPushSettingResponse.from(user);
    }

    public UserConnectedAccountResponse getConnectedAccounts(Long userId) {
        User user = findUserByIdWithProviders(userId);
        return UserConnectedAccountResponse.from(user);
    }

    @Transactional
    public UserConnectedAccountResponse linkConnectedAccount(
            Long userId,
            String providerPath,
            SocialAccountLinkRequest request
    ) {
        AuthProvider authProvider = parseLinkableProvider(providerPath);
        SocialUserInfo info = socialLoginFactory
                .getService(authProvider.name())
                .getUserInfo(request.getAccessToken());

        if (info.getProviderId() == null || info.getProviderId().isBlank()) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_FAILED);
        }

        User user = findUserByIdWithProviders(userId);

        Optional<UserProvider> existingGlobal = userProviderRepository
                .findByProviderAndProviderId(authProvider, info.getProviderId());
        if (existingGlobal.isPresent()) {
            User owner = existingGlobal.get().getUser();
            if (!owner.getId().equals(userId)) {
                throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_LINKED_TO_OTHER_USER);
            }
            return UserConnectedAccountResponse.from(user);
        }

        if (user.hasProvider(authProvider)) {
            user.getLinkedProviderId(authProvider).ifPresent(existingId -> {
                if (existingId != null && !existingId.isBlank()
                        && !existingId.equals(info.getProviderId())) {
                    throw new CustomException(ErrorCode.PROVIDER_ALREADY_CONNECTED_OTHER_ACCOUNT);
                }
            });
        }

        user.addProvider(authProvider, info.getProviderId());
        return UserConnectedAccountResponse.from(user);
    }

    @Transactional
    public UserConnectedAccountResponse unlinkConnectedAccount(Long userId, String providerPath) {
        AuthProvider authProvider = parseUnlinkableProvider(providerPath);
        User user = findUserByIdWithProviders(userId);

        if (!user.hasProvider(authProvider)) {
            throw new CustomException(ErrorCode.PROVIDER_NOT_CONNECTED);
        }

        if (user.getPassword() == null && user.getProviderLinkCount() <= 1) {
            throw new CustomException(ErrorCode.CANNOT_UNLINK_LAST_CREDENTIAL);
        }

        user.removeProvider(authProvider);
        return UserConnectedAccountResponse.from(user);
    }

    @Transactional
    public UserPushSettingResponse updateMyPushSettings(Long userId, UserUpdatePushSettingRequest request
    ) {
        User user = findUserById(userId);

        user.updatePushSettings(
                request.getTimeLetter(),
                request.getMindRecord(),
                request.getAfterNote()
        );

        return UserPushSettingResponse.from(user);
    }

    public List<ReceiverListResponse> getReceivers(Long userId) {

        User user = findUserById(userId);

        return userReceiverRepository.findAllByUser(user).stream()
                .map(ur -> ReceiverListResponse.from(ur.getReceiver()))
                .toList();
    }

    public ReceiverDetailResponse getReceiverDetail(Long userId, Long receiverId) {

        User user = findUserById(userId);

        UserReceiver userReceiver =
                userReceiverRepository.findByUserAndReceiverId(user, receiverId)
                        .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

        Receiver receiver = userReceiver.getReceiver();

        int dailyCount = 0;
        int timeLetterCount = 0;
        int afterNoteCount = 0;

        return ReceiverDetailResponse.from(
                receiver,
                dailyCount,
                timeLetterCount,
                afterNoteCount
        );
    }

    @Transactional
    public void recordActivity(Long userId) {
        User user = findUserById(userId);
        user.touchActivity();
    }

    @Transactional
    public UserCreateReceiverResponse createReceiver(
            Long userId,
            UserCreateReceiverRequest request
    ) {
        User user = findUserById(userId);

        Receiver receiver = Receiver.builder()
                .name(request.getName())
                .relation(request.getRelation())
                .phone(request.getPhone())
                .email(request.getEmail())
                .message(request.getMessage())
                .userId(user.getId())
                .build();

        receiverRepository.save(receiver);

        UserReceiver userReceiver = UserReceiver.builder()
                .user(user)
                .receiver(receiver)
                .build();

        userReceiverRepository.save(userReceiver);

        if (receiver.getEmail() != null && !receiver.getEmail().isBlank()) {
            try {
                authCodeMessageService.sendAuthCode(
                        receiver.getEmail(),
                        receiver.getAuthCode(),
                        user.getName(),
                        receiver.getName()
                );
            } catch (Exception e) {
                log.warn("Failed to send auth code via email for receiver {}: {}", receiver.getId(), e.getMessage());
            }
        }

        return UserCreateReceiverResponse.from(receiver.getId(), receiver.getAuthCode());
    }

    @Transactional
    public void updateReceiverMessage(Long userId, Long receiverId, UserUpdateReceiverMessageRequest request) {
        User user = findUserById(userId);

        UserReceiver userReceiver =
                userReceiverRepository.findByUserAndReceiverId(user, receiverId)
                        .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

        Receiver receiver = userReceiver.getReceiver();
        receiver.updateMessage(request.getMessage());
    }

    @Transactional
    public void deleteAccount(Long userId) {
        User user = findUserById(userId);
        
        // 1. Redis에서 해당 유저의 모든 refresh token 삭제
        tokenService.deleteAllUserTokens(userId);
        
        // 2. User 엔티티 삭제 (cascade로 providers도 자동 삭제됨)
        userRepository.delete(user);
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private User findUserByIdWithProviders(Long userId) {
        return userRepository.findWithProvidersById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private static AuthProvider parseLinkableProvider(String raw) {
        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_CONNECTED_ACCOUNT_PROVIDER);
        }
        if (provider == AuthProvider.LOCAL) {
            throw new CustomException(ErrorCode.INVALID_CONNECTED_ACCOUNT_PROVIDER);
        }
        return provider;
    }

    private static AuthProvider parseUnlinkableProvider(String raw) {
        AuthProvider provider;
        try {
            provider = AuthProvider.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_CONNECTED_ACCOUNT_PROVIDER);
        }
        if (provider == AuthProvider.LOCAL) {
            throw new CustomException(ErrorCode.CANNOT_UNLINK_LOCAL_PROVIDER);
        }
        return provider;
    }

    @Transactional
    public UserPatchReceiverResponse updateReceiver(Long userId, Long receiverId, UserPatchReceiverRequest request) {

        User user = findUserById(userId);
        UserReceiver userReceiver =
                userReceiverRepository.findByUserAndReceiverId(user, receiverId)
                        .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

        Receiver receiver = userReceiver.getReceiver();

        receiver.updateInfo(
                request.getName(),
                request.getRelation(),
                request.getPhone(),
                request.getEmail()
        );

        return UserPatchReceiverResponse.from(receiver);
    }


}
