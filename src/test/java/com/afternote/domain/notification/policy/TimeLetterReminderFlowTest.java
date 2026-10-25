package com.afternote.domain.notification.policy;

import com.afternote.domain.notification.dto.PushDeliveryResult;
import com.afternote.domain.notification.reminder.*;
import com.afternote.domain.notification.service.FcmPushSender;
import com.afternote.domain.timeletter.model.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.receiver.repository.*;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Real JPA rows, real policy selection and source guards; only external FCM is mocked. */
class TimeLetterReminderFlowTest {
    private AnnotationConfigApplicationContext context;
    private TimeLetterRepository letters;
    private NotificationDispatchRepository dispatches;
    private NotificationPolicyStore store;
    private TimeLetterReminderScheduler scheduler;
    private NotificationPolicyWorker worker;
    private FcmPushSender sender;
    private User user;
    private TransactionTemplate tx;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    protected DataSource createDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, this::createDataSource);
        context.register(NotificationPolicyTest.Config.class, ReminderConfig.class);
        context.refresh();
        letters = context.getBean(TimeLetterRepository.class);
        dispatches = context.getBean(NotificationDispatchRepository.class);
        store = context.getBean(NotificationPolicyStore.class);
        scheduler = context.getBean(TimeLetterReminderScheduler.class);
        worker = context.getBean(NotificationPolicyWorker.class);
        sender = context.getBean(FcmPushSender.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        user = context.getBean(UserRepository.class).saveAndFlush(User.builder().email(UUID.randomUUID()+"@test.com")
                .name("작성자").status(UserStatus.ACTIVE).provider(AuthProvider.LOCAL).build());
        lenient().when(sender.sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new PushDeliveryResult(1, 0, false, true));
    }
    @AfterEach void close() { context.close(); }
    private TimeLetter letter(int days) {
        return letters.saveAndFlush(TimeLetter.builder().user(user).title("편지")
                .sendAt(NOW.plusDays(days)).status(TimeLetterStatus.SCHEDULED).build());
    }
    private NotificationDispatch only() {
        assertThat(dispatches.count()).isEqualTo(1);
        return dispatches.findAll().get(0);
    }

    @Test void scheduledLetterProducesOne7DayPushWithConcreteNavigation() {
        TimeLetter letter=letter(7);
        scheduler.poll(); scheduler.poll();
        assertThat(only().getStatus()).isEqualTo(NotificationDispatch.Status.QUEUED);
        worker.process(user.getId());
        scheduler.poll(); worker.process(user.getId());
        assertThat(only().getStatus()).isEqualTo(NotificationDispatch.Status.PUSH_SENT);
        verify(sender, times(1)).sendToUserWithResult(eq(user.getId()), eq("AfterNote"),
                eq("예약하신 편지가 곧 전달돼요. 지금이면 수정할 수 있어요"), argThat(data ->
                        "TIME_LETTER_DETAIL".equals(data.get("destination"))
                        && letter.getId().toString().equals(data.get("timeLetterId"))
                        && "EDIT".equals(data.get("action"))
                        && "TIME_LETTER_REMINDER_7D".equals(data.get("type"))));
    }
    @Test void deletedSourceIsCancelledBeforeReservingDailySlot() {
        TimeLetter letter=letter(7); scheduler.poll(); letters.deleteById(letter.getId());
        worker.process(user.getId());
        assertThat(only().getStatus()).isEqualTo(NotificationDispatch.Status.CANCELLED);
        assertThat(only().getSelectedDate()).isNull();
        verifyNoInteractions(sender);
    }
    @Test void rescheduledLetterCancelsReadyReminderAndGeneratesNewIdentity() {
        TimeLetter letter=letter(7); scheduler.poll();
        Long oldId=only().getId();
        assertThat(store.prepare(user.getId(), NOW)).containsExactly(oldId);
        tx.executeWithoutResult(s -> letters.findById(letter.getId()).orElseThrow()
                .update(null, NOW.plusDays(7).minusHours(1), null, null));
        assertThat(store.claim(oldId, NOW)).isEmpty();
        assertThat(dispatches.findById(oldId).orElseThrow().getSelectedDate()).isNull();
        scheduler.poll(); worker.process(user.getId());
        assertThat(dispatches.findAll()).extracting(NotificationDispatch::getStatus)
                .containsExactlyInAnyOrder(NotificationDispatch.Status.CANCELLED, NotificationDispatch.Status.PUSH_SENT);
    }
    @Test void disabledPreferenceAfterEnqueueSuppressesPush() {
        letter(7); scheduler.poll();
        tx.executeWithoutResult(s -> context.getBean(UserRepository.class).findById(user.getId()).orElseThrow()
                .updatePushSettings(false, null, null));
        worker.process(user.getId());
        assertThat(only().getStatus()).isEqualTo(NotificationDispatch.Status.CANCELLED);
        verifyNoInteractions(sender);
    }
    @Test void thirtyDayReminderIsOneEEventNotDuplicatedAsCReturnHook() {
        TimeLetter letter=letter(30);
        Receiver receiver=context.getBean(ReceiverRepository.class).saveAndFlush(Receiver.builder()
                .name("소희").userId(user.getId()).build());
        context.getBean(TimeLetterReceiverRepository.class).saveAndFlush(TimeLetterReceiver.builder()
                .timeLetter(letter).receiver(receiver).deliveredAt(letter.getSendAt()).build());
        scheduler.poll(); scheduler.poll(); worker.process(user.getId());
        assertThat(only().getCategory()).isEqualTo(NotificationCategory.E);
        assertThat(only().getStatus()).isEqualTo(NotificationDispatch.Status.PUSH_SENT);
        verify(sender).sendToUserWithResult(eq(user.getId()), eq("AfterNote"),
                eq("소희님께 갈 편지가 한 달 뒤에 전달돼요"), argThat(data -> "VIEW".equals(data.get("action"))));
    }
    @Test void draftPostDeathAndMissedWindowDoNotProduceReminders() {
        letters.saveAndFlush(TimeLetter.builder().user(user).sendAt(NOW.plusDays(7)).status(TimeLetterStatus.DRAFT).build());
        letters.saveAndFlush(TimeLetter.builder().user(user).status(TimeLetterStatus.SCHEDULED)
                .deliveryMode(TimeLetterDeliveryMode.POST_DEATH).build());
        letter(5);
        scheduler.poll();
        assertThat(dispatches.count()).isZero();
        verifyNoInteractions(sender);
    }
    @Test void reminderExpiresIfDispatchDelayedBeyondOneDay() {
        letter(7); scheduler.poll();
        assertThat(store.prepare(user.getId(), NOW.plusDays(1))).isEmpty();
        assertThat(only().getStatus()).isEqualTo(NotificationDispatch.Status.CANCELLED);
    }
    @Configuration(proxyBeanMethods=false)
    @EnableJpaRepositories(basePackageClasses={TimeLetterRepository.class, TimeLetterReceiverRepository.class})
    @Import({TimeLetterReminderService.class, TimeLetterReminderScheduler.class, TimeLetterReminderGuard.class})
    static class ReminderConfig { }
}
