package com.afternote.domain.receiver.dto;

import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.user.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReceivedTimeLetterResponseTest {

    @Test
    @DisplayName("사후 타임레터는 실제 전달 시각이 기록된 뒤에만 내용을 공개한다")
    void postDeathContentBecomesAvailableAfterActualDelivery() {
        User user = User.builder()
                .email("sender@example.com")
                .password("password123!")
                .name("발신자")
                .build();
        Receiver receiver = Receiver.builder()
                .userId(1L)
                .name("수신자")
                .build();
        TimeLetter timeLetter = TimeLetter.builder()
                .user(user)
                .title("사후 편지")
                .status(TimeLetterStatus.SCHEDULED)
                .deliveryMode(TimeLetterDeliveryMode.POST_DEATH)
                .build();
        TimeLetterReceiver timeLetterReceiver = TimeLetterReceiver.builder()
                .timeLetter(timeLetter)
                .receiver(receiver)
                .build();

        ReceivedTimeLetterResponse beforeDelivery = ReceivedTimeLetterResponse.from(timeLetterReceiver);

        assertThat(beforeDelivery.getTitle()).isNull();
        assertThat(beforeDelivery.getDeliveredAt()).isNull();

        LocalDateTime deliveredAt = LocalDateTime.of(2026, 8, 21, 4, 0);
        timeLetterReceiver.markAsDelivered(deliveredAt);
        timeLetter.markAsSent();

        ReceivedTimeLetterResponse afterDelivery = ReceivedTimeLetterResponse.from(timeLetterReceiver);

        assertThat(afterDelivery.getTitle()).isEqualTo("사후 편지");
        assertThat(afterDelivery.getDeliveredAt()).isEqualTo(deliveredAt);
    }
}
