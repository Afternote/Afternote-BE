package com.afternote.domain.receiver.service;

import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.dto.ReceivedTimeLetterListResponse;
import com.afternote.domain.receiver.dto.ReceivedTimeLetterResponse;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.user.model.User;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReceivedServiceTimeLetterTest {

    private static final long RECEIVER_ID = 7L;

    @InjectMocks
    private ReceivedService receivedService;

    @Mock
    private TimeLetterReceiverRepository timeLetterReceiverRepository;
    @Mock
    private DeliveryConditionService deliveryConditionService;
    @Mock
    private S3Service s3Service;

    private User sender;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .email("sender@test.com")
                .password("password123!")
                .name("김철수")
                .build();
    }

    @Test
    @DisplayName("조건이 미충족이면 POST_DEATH는 목록에서 빼고 DATE는 남긴다")
    void listExcludesUnfulfilledPostDeath() {
        given(deliveryConditionService.isFulfilled(RECEIVER_ID, DeliveryContentType.TIME_LETTER))
                .willReturn(false);
        given(timeLetterReceiverRepository.findByReceiverIdWithTimeLetter(RECEIVER_ID))
                .willReturn(List.of(
                        link(dateLetter(TimeLetterStatus.SCHEDULED, LocalDateTime.now().plusDays(1)), 1L),
                        link(postDeathLetter(TimeLetterStatus.SCHEDULED), 2L)
                ));

        ReceivedTimeLetterListResponse response = receivedService.getTimeLetters(RECEIVER_ID);

        assertThat(response.getTimeLetters()).hasSize(1);
        assertThat(response.getTimeLetters().get(0).getTimeLetterReceiverId()).isEqualTo(1L);
        assertThat(response.getTimeLetters().get(0).getTitle()).isNull();
    }

    @Test
    @DisplayName("조건이 충족되면 POST_DEATH를 목록에 넣고 본문을 공개한다")
    void listIncludesFulfilledPostDeathWithContent() {
        given(deliveryConditionService.isFulfilled(RECEIVER_ID, DeliveryContentType.TIME_LETTER))
                .willReturn(true);
        given(timeLetterReceiverRepository.findByReceiverIdWithTimeLetter(RECEIVER_ID))
                .willReturn(List.of(link(postDeathLetter(TimeLetterStatus.SCHEDULED), 2L)));

        ReceivedTimeLetterListResponse response = receivedService.getTimeLetters(RECEIVER_ID);

        assertThat(response.getTimeLetters()).hasSize(1);
        assertThat(response.getTimeLetters().get(0).getTitle()).isEqualTo("사후 편지");
        assertThat(response.getTimeLetters().get(0).getSenderName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("DRAFT 타임레터는 조건 충족과 무관하게 수신 목록에서 뺀다")
    void listExcludesDraftEvenWhenFulfilled() {
        given(deliveryConditionService.isFulfilled(RECEIVER_ID, DeliveryContentType.TIME_LETTER))
                .willReturn(true);
        given(timeLetterReceiverRepository.findByReceiverIdWithTimeLetter(RECEIVER_ID))
                .willReturn(List.of(
                        link(postDeathLetter(TimeLetterStatus.DRAFT), 3L),
                        link(dateLetter(TimeLetterStatus.DRAFT, LocalDateTime.now().minusDays(1)), 4L)
                ));

        ReceivedTimeLetterListResponse response = receivedService.getTimeLetters(RECEIVER_ID);

        assertThat(response.getTimeLetters()).isEmpty();
    }

    @Test
    @DisplayName("POST_DEATH 상세는 조건 충족 시 본문을 공개하고 읽음 처리한다")
    void detailRevealsPostDeathWhenFulfilled() {
        TimeLetterReceiver link = link(postDeathLetter(TimeLetterStatus.SCHEDULED), 2L);
        given(timeLetterReceiverRepository.findByIdAndReceiverIdWithTimeLetter(2L, RECEIVER_ID))
                .willReturn(Optional.of(link));

        ReceivedTimeLetterResponse response = receivedService.getTimeLetter(RECEIVER_ID, 2L);

        verify(deliveryConditionService).requireFulfilled(RECEIVER_ID, DeliveryContentType.TIME_LETTER);
        assertThat(response.getTitle()).isEqualTo("사후 편지");
        assertThat(link.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("DRAFT 상세는 찾을 수 없음으로 처리한다")
    void detailRejectsDraft() {
        given(timeLetterReceiverRepository.findByIdAndReceiverIdWithTimeLetter(3L, RECEIVER_ID))
                .willReturn(Optional.of(link(postDeathLetter(TimeLetterStatus.DRAFT), 3L)));

        assertThatThrownBy(() -> receivedService.getTimeLetter(RECEIVER_ID, 3L))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TIME_LETTER_NOT_FOUND);
        verify(deliveryConditionService, never())
                .requireFulfilled(RECEIVER_ID, DeliveryContentType.TIME_LETTER);
    }

    private TimeLetter dateLetter(TimeLetterStatus status, LocalDateTime sendAt) {
        return TimeLetter.builder()
                .user(sender)
                .title("날짜 편지")
                .sendAt(sendAt)
                .status(status)
                .deliveryMode(TimeLetterDeliveryMode.DATE)
                .build();
    }

    private TimeLetter postDeathLetter(TimeLetterStatus status) {
        return TimeLetter.builder()
                .user(sender)
                .title("사후 편지")
                .sendAt(null)
                .status(status)
                .deliveryMode(TimeLetterDeliveryMode.POST_DEATH)
                .build();
    }

    private static TimeLetterReceiver link(TimeLetter letter, long id) {
        Receiver receiver = Receiver.builder().name("수신자").relation("아들").userId(1L).build();
        ReflectionTestUtils.setField(receiver, "id", RECEIVER_ID);
        TimeLetterReceiver timeLetterReceiver = TimeLetterReceiver.builder()
                .timeLetter(letter)
                .receiver(receiver)
                .deliveredAt(letter.getSendAt())
                .build();
        ReflectionTestUtils.setField(timeLetterReceiver, "id", id);
        return timeLetterReceiver;
    }
}
