package com.afternote.domain.notification.policy;

import com.afternote.domain.notification.dto.PushDeliveryResult;
import com.afternote.domain.notification.service.FcmPushSender;
import com.afternote.domain.receiver.model.*;
import com.afternote.domain.receiver.repository.*;
import com.afternote.domain.receiver.service.*;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.config.ReceiverInvitationProperties;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real invitation mutations, committed queue and policy guard. Only the external push provider is mocked. */
class ReceiverInvitationFlowTest {
    private AnnotationConfigApplicationContext context;
    private ReceiverInvitationService service;
    private ReceiverInvitationRepository invitations;
    private NotificationDispatchRepository dispatches;
    private NotificationPolicyWorker worker;
    private NotificationPolicyStore store;
    private FcmPushSender sender;
    private TransactionTemplate tx;
    private User owner;
    private User recipient;
    private LocalDateTime now;

    protected DataSource createDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, this::createDataSource);
        context.register(NotificationPolicyTest.Config.class, InvitationConfig.class);
        context.refresh();
        service = context.getBean(ReceiverInvitationService.class);
        invitations = context.getBean(ReceiverInvitationRepository.class);
        dispatches = context.getBean(NotificationDispatchRepository.class);
        worker = context.getBean(NotificationPolicyWorker.class);
        store = context.getBean(NotificationPolicyStore.class);
        sender = context.getBean(FcmPushSender.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        now = LocalDateTime.now(context.getBean(Clock.class));
        owner = user("발신자"); recipient = user("수신자");
        lenient().when(sender.sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new PushDeliveryResult(1, 0, false, true));
    }
    @AfterEach void close() { context.close(); }
    private User user(String name) {
        return context.getBean(UserRepository.class).saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@test.com").name(name).status(UserStatus.ACTIVE).provider(AuthProvider.LOCAL).build());
    }
    private void age(Long id, int days, LocalDateTime expiry) {
        tx.executeWithoutResult(s -> context.getBean("entityManager", EntityManager.class)
                .createQuery("update ReceiverInvitation i set i.createdAt = :created, i.expiresAt = :expiry where i.id = :id")
                .setParameter("created", now.minusDays(days)).setParameter("expiry", expiry).setParameter("id", id).executeUpdate());
    }
    private NotificationDispatch only() {
        assertThat(dispatches.count()).isEqualTo(1);
        return dispatches.findAll().get(0);
    }

    @Test void newInvitationExpiresIn14DaysAndOwnedLookupDoesNotExposeOtherUsers() {
        var invite = service.createInvitation(owner.getId());
        assertThat(invite.expiresAt()).isEqualTo(now.plusDays(14));
        assertThat(service.getOwnedInvitation(owner.getId(), invite.invitationId()).status()).isEqualTo(ReceiverInvitationStatus.PENDING);
        assertThatThrownBy(() -> service.getOwnedInvitation(recipient.getId(), invite.invitationId()))
                .isInstanceOfSatisfying(CustomException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECEIVER_INVITATION_NOT_FOUND));
    }
    @Test void acceptanceIsIdempotentAndExemptFromDailyCap() {
        var invite = service.createInvitation(owner.getId());
        store.enqueue(owner.getId(), "used-day", NotificationCategory.A, NotificationEvent.SCHEDULED,
                "AfterNote", "기존 안내", Map.of("destination", "HOME"), now);
        worker.process(owner.getId());
        var accepted = service.acceptInvitation(recipient.getId(), invite.invitationToken());
        assertThat(service.acceptInvitation(recipient.getId(), invite.invitationToken())).isEqualTo(accepted);
        assertThat(dispatches.count()).isEqualTo(2);
        assertThat(dispatches.findAll()).filteredOn(n -> n.getEvent() == NotificationEvent.ACCEPTANCE)
                .singleElement().satisfies(n -> {
                    assertThat(n.getStatus()).isEqualTo(NotificationDispatch.Status.PUSH_SENT);
                    assertThat(n.getSelectedDate()).isNull();
                    assertThat(n.getPayloadJson()).contains("RECEIVER_DETAIL", "receiverId");
                });
        verify(sender, times(2)).sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap());
    }
    @Test void sevenDayReminderIsCreatedOnlyOnceAndCancelledIfAcceptedBeforeSending() {
        var invite = service.createInvitation(owner.getId()); age(invite.invitationId(), 7, now.plusDays(7));
        service.processPending(invite.invitationId(), now); service.processPending(invite.invitationId(), now);
        Long reminderId = only().getId();
        assertThat(only().getPayloadJson()).contains("INVITATION_DETAIL", "invitationId", "REINVITE");
        service.acceptInvitation(recipient.getId(), invite.invitationToken());
        worker.process(owner.getId());
        assertThat(dispatches.findById(reminderId).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.CANCELLED);
        verify(sender, times(1)).sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap());
    }
    @Test void expiryAtExactBoundaryDoesNotCreateSevenDayReminderAndCannotBeAccepted() {
        var invite = service.createInvitation(owner.getId()); age(invite.invitationId(), 14, now);
        service.processPending(invite.invitationId(), now); service.processPending(invite.invitationId(), now);
        assertThat(only().getPayloadJson()).contains("RECEIVER_INVITATION_EXPIRED");
        assertThat(invitations.findById(invite.invitationId()).orElseThrow().getStatus()).isEqualTo(ReceiverInvitationStatus.EXPIRED);
        assertThatThrownBy(() -> service.acceptInvitation(recipient.getId(), invite.invitationToken()))
                .isInstanceOfSatisfying(CustomException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECEIVER_INVITATION_EXPIRED));
    }
    @Test void decliningTwiceDoesNotDuplicateAndCannotLaterAccept() {
        var invite = service.createInvitation(owner.getId());
        service.declineInvitation(recipient.getId(), invite.invitationToken());
        service.declineInvitation(recipient.getId(), invite.invitationToken());
        assertThat(only().getPayloadJson()).contains("RECEIVER_INVITATION_DECLINED", "REINVITE");
        assertThatThrownBy(() -> service.acceptInvitation(recipient.getId(), invite.invitationToken()))
                .isInstanceOfSatisfying(CustomException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECEIVER_INVITATION_CLOSED));
        assertThatThrownBy(() -> service.declineInvitation(user("다른 사람").getId(), invite.invitationToken()))
                .isInstanceOf(CustomException.class);
    }
    @Test void targetedInvitationConnectsExistingReceiverWithoutReplacingContactOrDuplicatingRelationship() {
        var receivers = context.getBean(ReceiverRepository.class);
        var links = context.getBean(UserReceiverRepository.class);
        Receiver target = receivers.saveAndFlush(Receiver.builder().userId(owner.getId()).name("엄마")
                .relation("가족").email("saved@example.com").build());
        links.saveAndFlush(new UserReceiver(owner, target));
        var invite = service.createTargetedInvitation(owner.getId(), target.getId());
        var accepted = service.acceptInvitation(recipient.getId(), invite.invitationToken());
        assertThat(accepted.receiverId()).isEqualTo(target.getId());
        assertThat(receivers.count()).isEqualTo(1); assertThat(links.count()).isEqualTo(1);
        assertThat(receivers.findById(target.getId()).orElseThrow()).satisfies(r -> {
            assertThat(r.getAcceptedUserId()).isEqualTo(recipient.getId());
            assertThat(r.getRelation()).isEqualTo("가족"); assertThat(r.getEmail()).isEqualTo("saved@example.com");
        });
    }
    @Test void targetedInvitationRejectsAnotherOwnersReceiver() {
        var target = context.getBean(ReceiverRepository.class).saveAndFlush(Receiver.builder()
                .userId(recipient.getId()).name("다른 수신자").build());
        assertThatThrownBy(() -> service.createTargetedInvitation(owner.getId(), target.getId()))
                .isInstanceOfSatisfying(CustomException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.RECEIVER_NOT_FOUND));
        assertThat(invitations.count()).isZero();
    }
    @Test void inactiveOwnerGetsNoExpiryPushAndOldSevenDayExpiryIsPreserved() {
        var invite = service.createInvitation(owner.getId()); age(invite.invitationId(), 7, now);
        tx.executeWithoutResult(s -> context.getBean("entityManager", EntityManager.class)
                .createQuery("update User u set u.status = :status where u.id = :id")
                .setParameter("status", UserStatus.INACTIVE).setParameter("id", owner.getId()).executeUpdate());
        service.processPending(invite.invitationId(), now);
        assertThat(invitations.findById(invite.invitationId()).orElseThrow().getStatus()).isEqualTo(ReceiverInvitationStatus.EXPIRED);
        assertThat(dispatches.count()).isZero(); verifyNoInteractions(sender);
    }
    @Test void parallelSchedulersCreateOneReminder() throws Exception {
        var invite = service.createInvitation(owner.getId()); age(invite.invitationId(), 7, now.plusDays(7));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            var a = pool.submit(() -> { start.await(); service.processPending(invite.invitationId(), now); return true; });
            var b = pool.submit(() -> { start.await(); service.processPending(invite.invitationId(), now); return true; });
            start.countDown(); assertThat(a.get(20, TimeUnit.SECONDS)).isTrue(); assertThat(b.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(only().getStatus()).isEqualTo(NotificationDispatch.Status.QUEUED);
        } finally { pool.shutdownNow(); }
    }
    @Test void schedulerProcessesOldPendingInvitations() {
        var invite = service.createInvitation(owner.getId()); age(invite.invitationId(), 7, now.plusDays(7));
        context.getBean(ReceiverInvitationScheduler.class).poll();
        context.getBean(ReceiverInvitationScheduler.class).poll();
        assertThat(only().getPayloadJson()).contains("PENDING_7D");
    }
    @Test void competingTargetedAcceptancesCannotOverwriteTheConnectedAccount() throws Exception {
        Receiver target = context.getBean(ReceiverRepository.class).saveAndFlush(Receiver.builder()
                .userId(owner.getId()).name("수신자").build());
        var first = service.createTargetedInvitation(owner.getId(), target.getId());
        var second = service.createTargetedInvitation(owner.getId(), target.getId());
        User other = user("다른 회원");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> acceptFirst = () -> { start.await(); return tryAccept(recipient.getId(), first.invitationToken()); };
            Callable<Integer> acceptSecond = () -> { start.await(); return tryAccept(other.getId(), second.invitationToken()); };
            var a = pool.submit(acceptFirst); var b = pool.submit(acceptSecond); start.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 1909);
            assertThat(context.getBean(ReceiverRepository.class).findById(target.getId()).orElseThrow().getAcceptedUserId())
                    .isIn(recipient.getId(), other.getId());
            assertThat(invitations.findAll()).extracting(ReceiverInvitation::getStatus)
                    .containsExactlyInAnyOrder(ReceiverInvitationStatus.ACCEPTED, ReceiverInvitationStatus.PENDING);
            assertThat(dispatches.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    private int tryAccept(Long userId, String token) {
        try { service.acceptInvitation(userId, token); return 200; }
        catch (CustomException ex) { return ex.getErrorCode().getCode(); }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = ReceiverInvitationRepository.class)
    @Import({ReceiverInvitationService.class, ReceiverInvitationScheduler.class, ReceiverInvitationNotificationGuard.class})
    static class InvitationConfig {
        @Bean ReceiverInvitationProperties invitationProperties() { return new ReceiverInvitationProperties(); }
    }
}
