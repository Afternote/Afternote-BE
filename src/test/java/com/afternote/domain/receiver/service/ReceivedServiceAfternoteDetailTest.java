package com.afternote.domain.receiver.service;

import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternoteReceiver;
import com.afternote.domain.afternote.repository.AfternoteRepository;
import com.afternote.domain.dailyquestion.repository.UserDailyQuestionRepository;
import com.afternote.domain.deepthought.repository.DeepThoughtRepository;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.diary.repository.DiaryRepository;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.ReceivedAfternoteDetailResponse;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.AfternoteReceiverRepository;
import com.afternote.domain.receiver.repository.DeepThoughtReceiverRepository;
import com.afternote.domain.receiver.repository.DiaryReceiverRepository;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.receiver.repository.UserDailyQuestionReceiverRepository;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceivedServiceAfternoteDetailTest {

    private static final long RECEIVER_ID = 7L;

    @InjectMocks
    private ReceivedService receivedService;

    @Mock
    private ReceiverRepository receiverRepository;
    @Mock
    private TimeLetterReceiverRepository timeLetterReceiverRepository;
    @Mock
    private AfternoteReceiverRepository afternoteReceiverRepository;
    @Mock
    private DeepThoughtReceiverRepository deepThoughtReceiverRepository;
    @Mock
    private DiaryReceiverRepository diaryReceiverRepository;
    @Mock
    private UserDailyQuestionReceiverRepository userDailyQuestionReceiverRepository;
    @Mock
    private TimeLetterRepository timeLetterRepository;
    @Mock
    private DeepThoughtRepository deepThoughtRepository;
    @Mock
    private DiaryRepository diaryRepository;
    @Mock
    private UserDailyQuestionRepository userDailyQuestionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private S3Service s3Service;
    @Mock
    private MindRecordReceiverService mindRecordReceiverService;
    @Mock
    private DeliveryConditionService deliveryConditionService;

    @Test
    @DisplayName("수신 애프터노트 상세 조회 - 모든 카테고리 응답 생성 매핑")
    void getAfternote_AllCategoriesHaveResponseFactory() {
        User sender = sampleUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));

        for (AfternoteCategoryType category : AfternoteCategoryType.values()) {
            long afternoteId = 100L + category.ordinal();
            Afternote afternote = Afternote.builder()
                    .user(sender)
                    .categoryType(category)
                    .title(category.name())
                    .sortOrder(category.ordinal())
                    .build();
            ReflectionTestUtils.setField(afternote, "id", afternoteId);

            given(afternoteReceiverRepository.findByAfternoteIdAndReceiverIdWithAfternote(afternoteId, RECEIVER_ID))
                    .willReturn(Optional.of(link(afternote)));

            ReceivedAfternoteDetailResponse response = receivedService.getAfternote(RECEIVER_ID, afternoteId);

            assertThat(response.getCategory()).isEqualTo(category);
            assertThat(response.getTitle()).isEqualTo(category.name());
            assertThat(response.getSenderName()).isEqualTo("tester");
        }

        verify(deliveryConditionService, times(AfternoteCategoryType.values().length))
                .requireFulfilled(RECEIVER_ID, DeliveryContentType.AFTERNOTE);
    }

    private static AfternoteReceiver link(Afternote afternote) {
        Receiver receiver = Receiver.builder()
                .name("수신자")
                .relation("친구")
                .userId(1L)
                .build();
        ReflectionTestUtils.setField(receiver, "id", RECEIVER_ID);
        return AfternoteReceiver.builder()
                .afternote(afternote)
                .receiver(receiver)
                .build();
    }

    private static User sampleUser(Long id) {
        User user = User.builder()
                .email("test@test.com")
                .password("pw")
                .name("tester")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.LOCAL)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
