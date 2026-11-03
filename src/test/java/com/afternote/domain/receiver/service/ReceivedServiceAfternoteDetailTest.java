package com.afternote.domain.receiver.service;

import com.afternote.domain.afternote.model.Afternote;
import com.afternote.domain.afternote.model.AfternoteCategoryType;
import com.afternote.domain.afternote.model.AfternoteReceiver;
import com.afternote.domain.afternote.model.AfternoteSecureContent;
import com.afternote.domain.afternote.service.relation.EncryptedKey;
import com.afternote.global.util.ChaChaEncryptionUtil;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock private ChaChaEncryptionUtil chaChaEncryptionUtil;

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
            assertThat(response.getCredentials()).isNull();
        }

        verify(deliveryConditionService, times(AfternoteCategoryType.values().length))
                .requireFulfilled(RECEIVER_ID, DeliveryContentType.AFTERNOTE);
    }

    @ParameterizedTest
    @EnumSource(value = AfternoteCategoryType.class, names = {"SOCIAL", "BUSINESS"})
    void authorizedDetailDecryptsStoredCredentialsAndKeepsActions(AfternoteCategoryType category) {
        ChaChaEncryptionUtil crypto = new ChaChaEncryptionUtil();
        ReflectionTestUtils.setField(crypto, "secretKey", "01234567890123456789012345678901");
        ReflectionTestUtils.setField(receivedService, "chaChaEncryptionUtil", crypto);
        User sender = sampleUser(1L);
        Afternote note = Afternote.builder().user(sender).categoryType(category)
                .title("계정 정리").sortOrder(0).actions(java.util.List.of("계정 삭제", "추모 계정 전환")).build();
        note.getSecureContents().add(AfternoteSecureContent.builder().afternote(note)
                .keyName(EncryptedKey.ACCOUNT_ID.value()).encryptedValue(crypto.encrypt("dummy-account")).build());
        note.getSecureContents().add(AfternoteSecureContent.builder().afternote(note)
                .keyName(EncryptedKey.ACCOUNT_PASSWORD.value()).encryptedValue(crypto.encrypt("dummy-password")).build());
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));
        given(afternoteReceiverRepository.findByAfternoteIdAndReceiverIdWithAfternote(10L, RECEIVER_ID))
                .willReturn(Optional.of(link(note)));

        var result = receivedService.getAfternote(RECEIVER_ID, 10L);
        assertThat(result.getCredentials().id()).isEqualTo("dummy-account");
        assertThat(result.getCredentials().password()).isEqualTo("dummy-password");
        assertThat(result.actions()).containsExactly("계정 삭제", "추모 계정 전환");
        assertThat(note.getSecureContents().get(0).getEncryptedValue()).isNotEqualTo("dummy-account");
    }

    @ParameterizedTest
    @EnumSource(value = AfternoteCategoryType.class, names = {"GALLERY", "PLAYLIST"})
    void nonAccountCategoriesNeverDecryptEvenIfLegacySecureContentExists(AfternoteCategoryType category) {
        User sender = sampleUser(1L);
        Afternote note = Afternote.builder().user(sender).categoryType(category).title("기록").sortOrder(0).build();
        note.getSecureContents().add(AfternoteSecureContent.builder().afternote(note)
                .keyName("account_password").encryptedValue("must-not-be-read").build());
        given(userRepository.findById(1L)).willReturn(Optional.of(sender));
        given(afternoteReceiverRepository.findByAfternoteIdAndReceiverIdWithAfternote(10L, RECEIVER_ID))
                .willReturn(Optional.of(link(note)));
        assertThat(receivedService.getAfternote(RECEIVER_ID, 10L).getCredentials()).isNull();
        verifyNoInteractions(chaChaEncryptionUtil);
    }

    @Test
    void unfulfilledConditionStopsBeforeLookupOrDecryption() {
        doThrow(new CustomException(ErrorCode.DELIVERY_CONDITION_NOT_MET)).when(deliveryConditionService)
                .requireFulfilled(RECEIVER_ID, DeliveryContentType.AFTERNOTE);
        assertThatThrownBy(() -> receivedService.getAfternote(RECEIVER_ID, 10L))
                .isInstanceOf(CustomException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.DELIVERY_CONDITION_NOT_MET);
        verifyNoInteractions(afternoteReceiverRepository, chaChaEncryptionUtil);
    }

    @Test
    void missingPublishedAssociationStopsBeforeDecryption() {
        given(afternoteReceiverRepository.findByAfternoteIdAndReceiverIdWithAfternote(10L, RECEIVER_ID))
                .willReturn(Optional.empty());
        assertThatThrownBy(() -> receivedService.getAfternote(RECEIVER_ID, 10L))
                .isInstanceOf(CustomException.class).extracting("errorCode").isEqualTo(ErrorCode.AFTERNOTE_NOT_FOUND);
        verifyNoInteractions(chaChaEncryptionUtil);
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
