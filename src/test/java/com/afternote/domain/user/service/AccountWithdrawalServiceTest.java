package com.afternote.domain.user.service;

import com.afternote.domain.afternote.repository.AfternoteRepository;
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
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.user.repository.WithdrawnUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountWithdrawalServiceTest {

    @InjectMocks
    private AccountWithdrawalService accountWithdrawalService;

    @Mock private UserRepository userRepository;
    @Mock private WithdrawnUserRepository withdrawnUserRepository;
    @Mock private TokenService tokenService;
    @Mock private TimeLetterRepository timeLetterRepository;
    @Mock private TimeLetterReceiverRepository timeLetterReceiverRepository;
    @Mock private TimeLetterMediaRepository timeLetterMediaRepository;
    @Mock private DiaryRepository diaryRepository;
    @Mock private DiaryReceiverRepository diaryReceiverRepository;
    @Mock private DeepThoughtRepository deepThoughtRepository;
    @Mock private DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    @Mock private DeepThoughtCategoryRepository deepThoughtCategoryRepository;
    @Mock private UserDailyQuestionRepository userDailyQuestionRepository;
    @Mock private UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;
    @Mock private AfternoteRepository afternoteRepository;
    @Mock private AfternoteReceiverRepository afternoteReceiverRepository;
    @Mock private DeliveryConditionRepository deliveryConditionRepository;
    @Mock private DeliveryVerificationRepository deliveryVerificationRepository;
    @Mock private EmotionRepository emotionRepository;
    @Mock private WeeklyReportRepository weeklyReportRepository;
    @Mock private UserReceiverRepository userReceiverRepository;
    @Mock private ReceiverRepository receiverRepository;
    @Mock private UserPushTokenService userPushTokenService;

    @Test
    @DisplayName("탈퇴 시 타임레터 수신자 조인을 먼저 삭제하고 이력을 남긴다")
    void withdraw_DeletesTimeLetterReceiversFirst() {
        User user = User.builder()
                .email("u@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", 10L);

        given(userRepository.findById(10L)).willReturn(Optional.of(user));
        given(timeLetterRepository.findIdsByUserId(10L)).willReturn(List.of(100L, 101L));
        given(diaryRepository.findIdsByUserId(10L)).willReturn(List.of());
        given(deepThoughtRepository.findIdsByUserId(10L)).willReturn(List.of());
        given(userDailyQuestionRepository.findIdsByUserId(10L)).willReturn(List.of());
        given(afternoteRepository.findIdsByUserId(10L)).willReturn(List.of());

        accountWithdrawalService.withdraw(10L);

        verify(tokenService).revokeUserAccess(10L);
        verify(tokenService).deleteAllUserTokens(10L);
        verify(timeLetterReceiverRepository).deleteByTimeLetterIdIn(List.of(100L, 101L));
        verify(timeLetterMediaRepository).deleteByTimeLetterIdIn(List.of(100L, 101L));
        verify(receiverRepository).deleteByUserId(10L);
        verify(userPushTokenService).deleteAllForUser(10L);

        ArgumentCaptor<com.afternote.domain.user.model.WithdrawnUser> captor =
                ArgumentCaptor.forClass(com.afternote.domain.user.model.WithdrawnUser.class);
        verify(withdrawnUserRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("u@test.com");
        assertThat(captor.getValue().getPreviousUserId()).isEqualTo(10L);

        verify(userRepository).delete(eq(user));
        verify(userRepository).flush();
    }
}
