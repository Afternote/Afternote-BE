package com.afternote.domain.user.service;

import com.afternote.domain.afternote.repository.AfternoteRepository;
import com.afternote.domain.auth.service.PasskeyService;
import com.afternote.domain.auth.service.TokenService;
import com.afternote.domain.notification.service.UserPushTokenService;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtCategoryRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.mindrecord.emotion.repository.EmotionRepository;
import com.afternote.domain.mindrecord.weekly.repository.WeeklyReportRepository;
import com.afternote.domain.receiver.repository.AfternoteReceiverRepository;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.repository.DeliveryVerificationRepository;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import com.afternote.domain.receiver.repository.UserReceiverRepository;
import com.afternote.domain.timeletter.repository.TimeLetterMediaRepository;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.WithdrawnUser;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.user.repository.WithdrawnUserRepository;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원 탈퇴: FK 순서로 종속 데이터를 지운 뒤 User hard delete.
 * cascade만으로는 time_letter_receiver 등 조인 테이블이 남아 500이 난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountWithdrawalService {

    private final UserRepository userRepository;
    private final WithdrawnUserRepository withdrawnUserRepository;
    private final TokenService tokenService;

    private final TimeLetterRepository timeLetterRepository;
    private final TimeLetterReceiverRepository timeLetterReceiverRepository;
    private final TimeLetterMediaRepository timeLetterMediaRepository;

    private final DiaryRepository diaryRepository;
    private final DiaryReceiverRepository diaryReceiverRepository;

    private final DeepThoughtRepository deepThoughtRepository;
    private final DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    private final DeepThoughtCategoryRepository deepThoughtCategoryRepository;

    private final UserDailyQuestionRepository userDailyQuestionRepository;
    private final UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;

    private final AfternoteRepository afternoteRepository;
    private final AfternoteReceiverRepository afternoteReceiverRepository;

    private final DeliveryConditionRepository deliveryConditionRepository;
    private final DeliveryVerificationRepository deliveryVerificationRepository;

    private final EmotionRepository emotionRepository;
    private final WeeklyReportRepository weeklyReportRepository;

    private final UserReceiverRepository userReceiverRepository;
    private final ReceiverRepository receiverRepository;
    private final UserPushTokenService userPushTokenService;
    private final PasskeyService passkeyService;

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String email = user.getEmail();

        // 1) 토큰 무효화 (refresh 전부 + access 차단용 revoke 플래그)
        tokenService.revokeUserAccess(userId);
        tokenService.deleteAllUserTokens(userId);

        // 2) 콘텐츠-수신자 조인 테이블 선행 삭제 (FK 500 방지)
        List<Long> timeLetterIds = timeLetterRepository.findIdsByUserId(userId);
        if (!timeLetterIds.isEmpty()) {
            timeLetterReceiverRepository.deleteByTimeLetterIdIn(timeLetterIds);
            timeLetterMediaRepository.deleteByTimeLetterIdIn(timeLetterIds);
        }

        List<Long> diaryIds = diaryRepository.findIdsByUserId(userId);
        if (!diaryIds.isEmpty()) {
            diaryReceiverRepository.deleteByDiaryIdIn(diaryIds);
        }

        List<Long> deepThoughtIds = deepThoughtRepository.findIdsByUserId(userId);
        if (!deepThoughtIds.isEmpty()) {
            deepThoughtReceiverRepository.deleteByDeepThoughtIdIn(deepThoughtIds);
        }

        List<Long> dailyQuestionIds = userDailyQuestionRepository.findIdsByUserId(userId);
        if (!dailyQuestionIds.isEmpty()) {
            userDailyQuestionReceiverRepository.deleteByUserDailyQuestionIdIn(dailyQuestionIds);
        }

        List<Long> afternoteIds = afternoteRepository.findIdsByUserId(userId);
        if (!afternoteIds.isEmpty()) {
            afternoteReceiverRepository.deleteByAfternoteIdIn(afternoteIds);
        }

        // 3) 전달 조건/검증·마인드 리포트 등 User 직접 참조
        deliveryConditionRepository.deleteByUserId(userId);
        deliveryVerificationRepository.deleteByUserId(userId);
        emotionRepository.deleteByUser_Id(userId);
        weeklyReportRepository.deleteByUser_Id(userId);

        // 4) 깊은생각 카테고리 (DeepThought.category FK 먼저 해제)
        deepThoughtRepository.clearCategoryByUserId(userId);
        deepThoughtCategoryRepository.deleteByUser_Id(userId);

        // 5) 수신자 (UserReceiver → Receiver). 다른 조인이 이미 정리된 뒤 삭제.
        userReceiverRepository.deleteByUser_Id(userId);
        receiverRepository.deleteByUserId(userId);

        userPushTokenService.deleteAllForUser(userId);
        passkeyService.deleteAllForUser(userId);

        // 6) 탈퇴 이력 저장 후 User hard delete (잔여 cascade: timeLetters, diaries, ...)
        withdrawnUserRepository.save(WithdrawnUser.of(email, userId));
        userRepository.delete(user);
        userRepository.flush();

        log.info("Account withdrawn. previousUserId={}, email={}", userId, email);
    }
}
