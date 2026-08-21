package com.afternote.domain.receiver.repository;

import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.timeletter.model.TimeLetter;
import com.afternote.domain.timeletter.model.TimeLetterDeliveryMode;
import com.afternote.domain.timeletter.model.TimeLetterStatus;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:time-letter-receiver;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class TimeLetterReceiverRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TimeLetterReceiverRepository timeLetterReceiverRepository;

    @Test
    @DisplayName("POST_DEATH 타임레터는 전달 시각 없이 수신자와 연결된다")
    void savePostDeathTimeLetterReceiverWithoutDeliveredAt() {
        User user = entityManager.persistAndFlush(User.builder()
                .email("post-death@example.com")
                .password("password123!")
                .name("테스트 사용자")
                .status(UserStatus.ACTIVE)
                .build());

        Receiver receiver = entityManager.persistAndFlush(Receiver.builder()
                .userId(user.getId())
                .name("수신자")
                .email("receiver@example.com")
                .build());

        TimeLetter timeLetter = entityManager.persistAndFlush(TimeLetter.builder()
                .user(user)
                .title("사후 전달 타임레터")
                .sendAt(null)
                .status(TimeLetterStatus.SCHEDULED)
                .deliveryMode(TimeLetterDeliveryMode.POST_DEATH)
                .build());

        TimeLetterReceiver saved = timeLetterReceiverRepository.saveAndFlush(
                TimeLetterReceiver.builder()
                        .timeLetter(timeLetter)
                        .receiver(receiver)
                        .build()
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getDeliveredAt()).isNull();
    }

}
