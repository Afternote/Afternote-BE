package com.afternote.domain.user.service;

import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.UserReceiver;
import com.afternote.domain.receiver.repository.AfternoteReceiverRepository;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.repository.DeliveryVerificationRepository;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수신자 삭제.
 * 정책: 콘텐츠(타임레터·애프터노트·일기 등)에 연결된 수신자는 삭제 거부(409).
 * 연결이 없으면 전달조건/인증이력·UserReceiver·Receiver 를 hard delete.
 */
@Service
@RequiredArgsConstructor
public class ReceiverDeletionService {

    private final UserRepository userRepository;
    private final UserReceiverRepository userReceiverRepository;
    private final ReceiverRepository receiverRepository;

    private final TimeLetterReceiverRepository timeLetterReceiverRepository;
    private final AfternoteReceiverRepository afternoteReceiverRepository;
    private final DiaryReceiverRepository diaryReceiverRepository;
    private final DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    private final UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;

    private final DeliveryConditionRepository deliveryConditionRepository;
    private final DeliveryVerificationRepository deliveryVerificationRepository;

    @Transactional
    public void deleteReceiver(Long userId, Long receiverId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        UserReceiver userReceiver = userReceiverRepository.findByUserAndReceiverId(user, receiverId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECEIVER_NOT_FOUND));

        if (isLinkedToContent(receiverId)) {
            throw new CustomException(ErrorCode.RECEIVER_IN_USE);
        }

        deliveryConditionRepository.deleteByReceiverId(receiverId);
        deliveryVerificationRepository.deleteByReceiverId(receiverId);

        Receiver receiver = userReceiver.getReceiver();
        userReceiverRepository.delete(userReceiver);
        receiverRepository.delete(receiver);
    }

    private boolean isLinkedToContent(Long receiverId) {
        return timeLetterReceiverRepository.existsByReceiverId(receiverId)
                || afternoteReceiverRepository.existsByReceiverId(receiverId)
                || diaryReceiverRepository.existsByReceiverId(receiverId)
                || deepThoughtReceiverRepository.existsByReceiverId(receiverId)
                || userDailyQuestionReceiverRepository.existsByReceiverId(receiverId);
    }
}
