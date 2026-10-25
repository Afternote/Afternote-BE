package com.afternote.domain.receiver.service;

import com.afternote.domain.notification.policy.NotificationPolicyStore;
import com.afternote.domain.notification.policy.NotificationCategory;
import com.afternote.domain.notification.policy.NotificationEvent;
import com.afternote.domain.receiver.dto.OwnedInvitationResponse;
import com.afternote.domain.user.model.UserStatus;
import org.springframework.transaction.annotation.Isolation;
import java.util.HashMap;
import java.util.Set;
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
    private final NotificationPolicyStore notifications;


    @Transactional
    public ReceiverInvitationCreateResponse createInvitation(Long inviterUserId) {
        return create(inviterUserId, null);
    }

    @Transactional
    public ReceiverInvitationCreateResponse createTargetedInvitation(Long inviterUserId, Long receiverId) {
        return create(inviterUserId, receiverId);
    }

    private ReceiverInvitationCreateResponse create(Long inviterUserId, Long receiverId) {
        User owner = lockOwner(inviterUserId);
        if (owner.getStatus() != UserStatus.ACTIVE) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }
        if (receiverId != null) {
            Receiver target = receiverRepository.findById(receiverId)
                    .filter(r -> r.getUserId().equals(inviterUserId))
                    .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));
            if (target.getAcceptedUserId() != null) throw new CustomException(ErrorCode.RECEIVER_ALREADY_REGISTERED);
        }

        String rawToken = generateToken();
        LocalDateTime expiresAt = now().plusDays(properties.getExpirationDays());
        ReceiverInvitation invitation = receiverInvitationRepository.save(
                new ReceiverInvitation(inviterUserId, hashToken(rawToken), expiresAt)
        );

        if (receiverId != null) {
            invitation.target(receiverId);
            Receiver target = receiverRepository.findById(receiverId).orElseThrow();
            notifications.enqueueWithChannels(inviterUserId, "INVITATION:" + invitation.getId() + ":SENT",
                    NotificationCategory.D, NotificationEvent.SCHEDULED, owner.getName() + "님이 당신을 수신자로 등록했어요",
                    java.util.Map.of("type", "RECEIVER_INVITATION_SENT", "destination", "INVITATION", "invitationId", invitation.getId().toString(),
                            "receiverId", receiverId.toString(), "linkId", rawToken, "action", "VIEW"), now(),
                    com.afternote.domain.notification.policy.NotificationChannel.ALIMTALK, target.getPhone(), target.getAcceptedUserId(), true);
        }
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceiverInvitationAcceptResponse acceptInvitation(Long acceptedUserId, String rawToken) {
        ReceiverInvitation invitation = lockInvitation(rawToken);
        LocalDateTime now = now();

        if (invitation.getStatus() == ReceiverInvitationStatus.ACCEPTED) {
            if (acceptedUserId.equals(invitation.getAcceptedUserId())) {
                User inviter = requireUser(invitation.getInviterUserId());
                return new ReceiverInvitationAcceptResponse(invitation.getReceiverId(), inviter.getName());
            }
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_ALREADY_ACCEPTED);
        }
        if (invitation.getStatus() == ReceiverInvitationStatus.DECLINED) {
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_CLOSED);
        }
        if (invitation.getStatus() == ReceiverInvitationStatus.EXPIRED || invitation.isExpired(now)) {
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
            Receiver receiver;
            if (invitation.getReceiverId() == null) {
                receiver = receiverRepository.saveAndFlush(Receiver.fromAcceptedInvitation(acceptedUser, inviter.getId()));
            } else {
                receiver = receiverRepository.findById(invitation.getReceiverId())
                        .filter(r -> r.getUserId().equals(inviter.getId()))
                        .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));
                if (receiver.getAcceptedUserId() != null) throw new CustomException(ErrorCode.RECEIVER_ALREADY_REGISTERED);
                receiver.connectAcceptedUser(acceptedUser.getId());
            }
            if (userReceiverRepository.findByUserAndReceiverId(inviter, receiver.getId()).isEmpty()) {
                userReceiverRepository.save(new UserReceiver(inviter, receiver));
            }
            invitation.accept(acceptedUser.getId(), receiver.getId(), now);
            notifyOwner(invitation, "ACCEPTED", receiver.getName() + "님이 수신자로 연결됐어요", now);
            return new ReceiverInvitationAcceptResponse(receiver.getId(), inviter.getName());
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.RECEIVER_ALREADY_REGISTERED);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void declineInvitation(Long userId, String rawToken) {
        ReceiverInvitation invitation = lockInvitation(rawToken);
        if (invitation.getStatus() == ReceiverInvitationStatus.DECLINED
                && userId.equals(invitation.getDeclinedUserId())) return;
        if (invitation.getStatus() != ReceiverInvitationStatus.PENDING) {
            throw new CustomException(ErrorCode.RECEIVER_INVITATION_CLOSED);
        }
        if (invitation.isExpired(now())) throw new CustomException(ErrorCode.RECEIVER_INVITATION_EXPIRED);
        if (userId.equals(invitation.getInviterUserId())) throw new CustomException(ErrorCode.RECEIVER_INVITATION_SELF_ACCEPT);
        User invitee = requireUser(userId);
        invitation.decline(userId);
        notifyOwner(invitation, "DECLINED", invitee.getName() + "님이 초대를 수락하지 않았어요", now());
    }

    /** Both scheduler and API mutations acquire owner before invitation, avoiding lock inversion. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processPending(Long invitationId, LocalDateTime at) {
        Long ownerId = receiverInvitationRepository.findInviterIdById(invitationId).orElse(null);
        if (ownerId == null) return;
        User owner = lockOwner(ownerId);
        ReceiverInvitation invitation = receiverInvitationRepository.findByIdForUpdate(invitationId).orElse(null);
        if (invitation == null || invitation.getStatus() != ReceiverInvitationStatus.PENDING) return;
        boolean active = owner.getStatus() == UserStatus.ACTIVE;
        if (invitation.isExpired(at)) {
            invitation.expire();
            if (active) notifyOwner(invitation, "EXPIRED", "보내신 수신자 초대가 만료됐어요", at);
        } else if (active && invitation.getCreatedAt() != null && !at.isBefore(invitation.getCreatedAt().plusDays(7))) {
            notifyOwner(invitation, "PENDING_7D", "수신자가 아직 초대를 확인하지 않았어요", at);
        }
    }

    public OwnedInvitationResponse getOwnedInvitation(Long ownerId, Long invitationId) {
        ReceiverInvitation i = receiverInvitationRepository.findById(invitationId)
                .filter(v -> v.getInviterUserId().equals(ownerId))
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_INVITATION_NOT_FOUND));
        return new OwnedInvitationResponse(
                i.getId(), i.getReceiverId(), effectiveStatus(i, now()), i.getExpiresAt());
    }

    private static ReceiverInvitationStatus effectiveStatus(ReceiverInvitation i, LocalDateTime at) {
        return i.getStatus() == ReceiverInvitationStatus.PENDING && i.isExpired(at)
                ? ReceiverInvitationStatus.EXPIRED : i.getStatus();
    }

    private User lockOwner(Long ownerId) {
        return userRepository.findByIdForPushUpdate(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private ReceiverInvitation lockInvitation(String rawToken) {
        String hash = hashToken(rawToken);
        Long ownerId = receiverInvitationRepository.findInviterIdByTokenHash(hash)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_INVITATION_NOT_FOUND));
        lockOwner(ownerId);
        return receiverInvitationRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_INVITATION_NOT_FOUND));
    }

    private void notifyOwner(ReceiverInvitation i, String kind, String body, LocalDateTime at) {
        User owner = requireUser(i.getInviterUserId());
        if (owner.getStatus() != UserStatus.ACTIVE) return;
        var data = new HashMap<String, String>();
        data.put("type", "RECEIVER_INVITATION_" + kind);
        data.put("invitationId", i.getId().toString());
        data.put("destination", i.getReceiverId() == null ? "INVITATION_DETAIL" : "RECEIVER_DETAIL");
        if (i.getReceiverId() != null) data.put("receiverId", i.getReceiverId().toString());
        data.put("action", Set.of("PENDING_7D", "DECLINED").contains(kind) ? "REINVITE" : "VIEW");
        if (kind.equals("DECLINED")) data.put("secondaryAction", "DELETE");
        notifications.enqueue(i.getInviterUserId(), "INVITATION:" + i.getId() + ":" + kind,
                NotificationCategory.D,
                kind.equals("ACCEPTED") ? NotificationEvent.ACCEPTANCE
                        : NotificationEvent.SCHEDULED,
                "AfterNote", body, data, at);
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
