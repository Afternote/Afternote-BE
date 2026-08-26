package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterBlock;
import com.afternote.domain.timeletter.model.TimeLetterBlockType;
import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.user.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivedTimeLetterResponseTest {

    @Test
    @DisplayName("DATE 타임레터는 sendAt이 지나지 않으면 제목·본문을 숨긴다")
    void dateLetterHidesContentBeforeSendAt() {
        TimeLetter letter = timeLetter(
                TimeLetterDeliveryMode.DATE,
                TimeLetterStatus.SCHEDULED,
                LocalDateTime.now().plusDays(1)
        );
        ReceivedTimeLetterResponse response = ReceivedTimeLetterResponse.from(receiverOf(letter));

        assertThat(response.getTitle()).isNull();
        assertThat(response.getBlocks()).isEmpty();
        assertThat(response.getSenderName()).isNull();
        assertThat(response.getSendAt()).isEqualTo(letter.getSendAt());
    }

    @Test
    @DisplayName("DATE 타임레터는 sendAt이 지나면 제목·본문을 공개한다")
    void dateLetterRevealsContentAfterSendAt() {
        TimeLetter letter = timeLetter(
                TimeLetterDeliveryMode.DATE,
                TimeLetterStatus.SCHEDULED,
                LocalDateTime.now().minusMinutes(1)
        );
        ReceivedTimeLetterResponse response = ReceivedTimeLetterResponse.from(receiverOf(letter));

        assertThat(response.getTitle()).isEqualTo("테스트 제목");
        assertThat(response.getBlocks()).hasSize(1);
        assertThat(response.getBlocks().get(0).getTextContent()).isEqualTo("본문");
        assertThat(response.getSenderName()).isEqualTo("김철수");
    }

    @Test
    @DisplayName("POST_DEATH 타임레터는 sendAt이 없어도 제목·본문을 공개한다")
    void postDeathLetterRevealsContentWithoutSendAt() {
        TimeLetter letter = timeLetter(
                TimeLetterDeliveryMode.POST_DEATH,
                TimeLetterStatus.SCHEDULED,
                null
        );
        ReceivedTimeLetterResponse response = ReceivedTimeLetterResponse.from(receiverOf(letter));

        assertThat(response.getTitle()).isEqualTo("테스트 제목");
        assertThat(response.getBlocks()).hasSize(1);
        assertThat(response.getBlocks().get(0).getTextContent()).isEqualTo("본문");
        assertThat(response.getSenderName()).isEqualTo("김철수");
        assertThat(response.getSendAt()).isNull();
    }

    private static TimeLetter timeLetter(
            TimeLetterDeliveryMode mode,
            TimeLetterStatus status,
            LocalDateTime sendAt
    ) {
        User sender = User.builder()
                .email("sender@test.com")
                .password("password123!")
                .name("김철수")
                .build();
        TimeLetter letter = TimeLetter.builder()
                .user(sender)
                .title("테스트 제목")
                .sendAt(sendAt)
                .status(status)
                .deliveryMode(mode)
                .build();
        letter.replaceBlocks(List.of(
                TimeLetterBlock.builder()
                        .blockType(TimeLetterBlockType.TEXT)
                        .blockOrder(1)
                        .textContent("본문")
                        .build()
        ));
        return letter;
    }

    private static TimeLetterReceiver receiverOf(TimeLetter letter) {
        Receiver receiver = Receiver.builder().name("수신자").relation("아들").userId(1L).build();
        TimeLetterReceiver timeLetterReceiver = TimeLetterReceiver.builder()
                .timeLetter(letter)
                .receiver(receiver)
                .deliveredAt(letter.getSendAt())
                .build();
        ReflectionTestUtils.setField(timeLetterReceiver, "id", 11L);
        return timeLetterReceiver;
    }
}
