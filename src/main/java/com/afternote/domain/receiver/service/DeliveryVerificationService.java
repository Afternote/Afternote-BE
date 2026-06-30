package com.afternote.domain.receiver.service;

import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.model.DeliveryVerification;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.VerificationStatus;
import com.afternote.domain.receiver.repository.DeliveryVerificationRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliveryVerificationService {

    private final DeliveryVerificationRepository deliveryVerificationRepository;
    private final ReceiverRepository receiverRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final com.afternote.domain.delivery.service.DeliveryConditionService deliveryConditionService;

    @Transactional
    public DeliveryVerification submitVerification(String authCode, String deathCertUrl, String familyRelationCertUrl) {
        Receiver receiver = receiverRepository.findByAuthCode(authCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_AUTH_CODE));

        User user = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        boolean hasDeathCert = StringUtils.hasText(deathCertUrl);
        boolean hasFamilyCert = StringUtils.hasText(familyRelationCertUrl);

        if (!hasDeathCert && !hasFamilyCert) {
            throw new CustomException(ErrorCode.INVALID_DELIVERY_CONDITION);
        }

        if (hasDeathCert && !s3Service.isManagedObjectKeyInDirectory(deathCertUrl, "documents")) {
            throw new CustomException(ErrorCode.INVALID_DELIVERY_CONDITION);
        }

        if (hasFamilyCert && !s3Service.isManagedObjectKeyInDirectory(familyRelationCertUrl, "documents")) {
            throw new CustomException(ErrorCode.INVALID_DELIVERY_CONDITION);
        }

        String deathCertKey = hasDeathCert ? s3Service.extractStorageKey(deathCertUrl) : null;
        String familyRelationCertKey = hasFamilyCert ? s3Service.extractStorageKey(familyRelationCertUrl) : null;

        if (deliveryVerificationRepository.existsByUserIdAndReceiverIdAndStatus(
                user.getId(), receiver.getId(), VerificationStatus.PENDING)) {
            throw new CustomException(ErrorCode.VERIFICATION_ALREADY_SUBMITTED);
        }

        DeliveryVerification verification = DeliveryVerification.builder()
                .userId(user.getId())
                .receiverId(receiver.getId())
            .deathCertificateUrl(deathCertKey)
            .familyRelationCertificateUrl(familyRelationCertKey)
                .build();

        return deliveryVerificationRepository.save(verification);
    }

    public DeliveryVerification getVerificationStatus(String authCode) {
        Receiver receiver = receiverRepository.findByAuthCode(authCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_AUTH_CODE));

        User user = userRepository.findById(receiver.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return deliveryVerificationRepository.findFirstByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.VERIFICATION_NOT_FOUND));
    }

    public List<DeliveryVerification> getPendingVerifications() {
        return deliveryVerificationRepository.findByStatus(VerificationStatus.PENDING);
    }

    public DeliveryVerification getVerificationDetail(Long verificationId) {
        return deliveryVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.VERIFICATION_NOT_FOUND));
    }

    @Transactional
    public DeliveryVerification approveVerification(Long verificationId, String adminNote) {
        DeliveryVerification verification = deliveryVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new CustomException(ErrorCode.VERIFICATION_ALREADY_PROCESSED);
        }

        verification.approve(adminNote);

        // 해당 (발신자, 수신자)에 설정된 RECEIVER_REQUEST 조건들을 충족 처리한다.
        deliveryConditionService.fulfillByReceiverRequest(
                verification.getUserId(), verification.getReceiverId());

        return verification;
    }

    @Transactional
    public DeliveryVerification rejectVerification(Long verificationId, String adminNote) {
        DeliveryVerification verification = deliveryVerificationRepository.findById(verificationId)
                .orElseThrow(() -> new CustomException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (verification.getStatus() != VerificationStatus.PENDING) {
            throw new CustomException(ErrorCode.VERIFICATION_ALREADY_PROCESSED);
        }

        verification.reject(adminNote);

        return verification;
    }
}
