package com.afternote.domain.receiver.service;

import com.afternote.domain.receiver.dto.ReceiverInvitationAcceptResponse;
import com.afternote.domain.receiver.dto.ReceiverInvitationCreateResponse;
import com.afternote.domain.receiver.dto.ReceiverInvitationResponse;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.ReceiverInvitation;
import com.afternote.domain.receiver.model.ReceiverInvitationStatus;
import com.afternote.domain.receiver.model.UserReceiver;
import com.afternote.domain.receiver.repository.ReceiverInvitationRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.config.ReceiverInvitationProperties;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiverInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final ReceiverInvitationRepository receiverInvitationRepository;
    private final ReceiverRepository receiverRepository;
    private final UserReceiverRepository userReceiverRepository;
    private final UserRepository userRepository;
    private final ReceiverInvitationProperties properties;
    private final Clock clock;

    @Transactional
    public ReceiverInvitationCreateResponse createInvitation(Long inviterUserId) {
        requireUser(inviterUserId);

        String rawToken = generateToken();
        LocalDateTime expiresAt = now().plusDays(properties.getExpirationDays());
        ReceiverInvitation invitation = receiverInvitationRepository.save(
                new ReceiverInvitation(inviterUserId, hashToken(rawToken), expiresAt)
        );

        return new ReceiverInvitationCreateResponse(
                invitation.getId(),
                rawToken,
                properties.normalizedBaseUrl() + "/" + rawToken,
                invitation.getExpiresAt()
        );
    }

    public ReceiverInvitationResponse getInvitation(String rawToken) {
        ReceiverInvitation invitation = findInvitation(rawToken);
        User inviter = requireUser(invitation.getInviterUserId());
        return ReceiverInvitationResponse.from(invitation, inviter, now());
    }

    @Transactional
    public ReceiverInvitationAcceptResponse acceptInvitation(Long acceptedUserId, String rawToken) {
        ReceiverInvitation invitation = receiverInvitationRepository
                .findByTokenHashForUpdate(hashToken(rawToken))
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_INVITATION_NOT_FOUND));
        LocalDateTime now = now();

        if (invitation.getStatus() == ReceiverInvitationStatus.ACCEPTED) {
            if (acceptedUserId.equals(invitation.getAcceptedUserId())) {
                User inviter = requireUser(invitation.getInviterUserId());
                return new ReceiverInvitationAcceptResponse(invitation.getReceiverId(), inviter.getName());
            }
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_ALREADY_ACCEPTED);
        }
        if (invitation.isExpired(now)) {
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_EXPIRED);
        }
        if (acceptedUserId.equals(invitation.getInviterUserId())) {
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_SELF_ACCEPT);
        }

        User acceptedUser = requireUser(acceptedUserId);
        User inviter = requireUser(invitation.getInviterUserId());
        if (receiverRepository.existsByUserIdAndAcceptedUserId(inviter.getId(), acceptedUser.getId())) {
            throw new CustomException(ErrorCode.RECEIVER_ALREADY_REGISTERED);
        }

        try {
            Receiver receiver = receiverRepository.saveAndFlush(
                    Receiver.fromAcceptedInvitation(acceptedUser, inviter.getId())
            );
            userReceiverRepository.save(new UserReceiver(inviter, receiver));
            invitation.accept(acceptedUser.getId(), receiver.getId(), now);
            return new ReceiverInvitationAcceptResponse(receiver.getId(), inviter.getName());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.RECEIVER_ALREADY_REGISTERED);
        }
    }

    private ReceiverInvitation findInvitation(String rawToken) {
        return receiverInvitationRepository.findByTokenHash(hashToken(rawToken))
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_INVITATION_NOT_FOUND));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_NOT_FOUND);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
