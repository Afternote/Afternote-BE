package com.afternote.domain.notification.policy;

import com.afternote.domain.notification.dto.PushDeliveryResult;
import com.afternote.domain.notification.service.FcmPushSender;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationPolicyTest {
    private AnnotationConfigApplicationContext context;
    private NotificationPolicyStore store;
    private NotificationDispatchRepository repository;
    private UserRepository users;
    private FcmPushSender sender;
    private Long userId;
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 15, 12, 0);

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, this::createDataSource);
        context.register(Config.class);
        context.refresh();
        store = context.getBean(NotificationPolicyStore.class);
        repository = context.getBean(NotificationDispatchRepository.class);
        users = context.getBean(UserRepository.class);
        sender = context.getBean(FcmPushSender.class);
        userId = users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@test.com")
                .name("test").status(UserStatus.ACTIVE).provider(AuthProvider.LOCAL).build()).getId();
    }
    protected DataSource createDataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
    @AfterEach void close() { context.close(); }
    private Long enqueue(String key, NotificationCategory category) {
        return store.enqueue(userId, key, category, NotificationEvent.SCHEDULED,
                "AfterNote", "test", Map.of("destination", "HOME"), DAY);
    }
    @Test void priorityCapAndImmediateExemptionPersist() {
        Long b = enqueue("b", NotificationCategory.B);
        Long a = enqueue("a", NotificationCategory.A);
        assertThat(store.prepare(userId, DAY)).containsExactly(a);
        assertThat(repository.findById(b).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.IN_APP_ONLY);
        assertThat(store.claim(a, DAY)).isPresent();
        store.complete(a, NotificationDispatch.Status.PUSH_SENT, null);
        Long f = enqueue("f", NotificationCategory.F);
        assertThat(store.prepare(userId, DAY)).isEmpty();
        assertThat(repository.findById(f).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.IN_APP_ONLY);
        Long immediate = repository.saveAndFlush(new NotificationDispatch(userId, "accept", NotificationCategory.D,
                NotificationEvent.ACCEPTANCE, "title", "body", "{}", DAY)).getId();
        assertThat(store.prepare(userId, DAY)).containsExactly(immediate);
        assertThat(store.claim(immediate, DAY)).isPresent();
    }
    @Test void nightAndMorningBoundariesExceptChosenBTime() {
        assertThat(NotificationTiming.allowedAt(NotificationCategory.A, DAY.withHour(22)))
                .isEqualTo(DAY.plusDays(1).withHour(8).withMinute(0));
        assertThat(NotificationTiming.allowedAt(NotificationCategory.F, DAY.withHour(7)))
                .isEqualTo(DAY.withHour(8));
        assertThat(NotificationTiming.allowedAt(NotificationCategory.A, DAY.withHour(8))).isEqualTo(DAY.withHour(8));
        assertThat(NotificationTiming.allowedAt(NotificationCategory.B, DAY.withHour(23))).isEqualTo(DAY.withHour(23));
    }
    @Test void staleQueuedAndReadyAreDeferredWithoutStarvingNightB() {
        Long ready = enqueue("ready", NotificationCategory.A);
        store.prepare(userId, DAY);
        Long queued = enqueue("queued", NotificationCategory.C);
        Long b = enqueue("night-b", NotificationCategory.B);
        assertThat(store.prepare(userId, DAY.plusDays(1).withHour(23))).containsExactly(b);
        LocalDateTime morning = DAY.plusDays(2).withHour(8);
        assertThat(repository.findById(ready).orElseThrow().getDueAt()).isEqualTo(morning);
        assertThat(repository.findById(queued).orElseThrow().getDueAt()).isEqualTo(morning);
        // After B's claim the deferred user's records must not occupy the polling window overnight.
        store.claim(b, DAY.plusDays(1).withHour(23));
        assertThat(repository.findDueUsers(DAY.plusDays(1).withHour(23),
                List.of(NotificationDispatch.Status.QUEUED, NotificationDispatch.Status.READY), PageRequest.of(0, 200))).isEmpty();
    }
    @Test void concurrentRolloverClaimsKeepOneScheduledSlot() throws Exception {
        Long first = enqueue("yesterday-1", NotificationCategory.A);
        store.prepare(userId, DAY);
        Long second = enqueue("yesterday-2", NotificationCategory.F);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.executeWithoutResult(s -> repository.findById(second).orElseThrow().select(DAY.minusDays(1)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> one = pool.submit(() -> { start.await(); return store.claim(first, DAY.plusDays(1)).isPresent(); });
            Future<Boolean> two = pool.submit(() -> { start.await(); return store.claim(second, DAY.plusDays(1)).isPresent(); });
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { pool.shutdownNow(); }
    }
    @Test void producerIsIdempotentAndInactiveRecipientsCancelled() {
        Long id = enqueue("same-event", NotificationCategory.E);
        assertThat(enqueue("same-event", NotificationCategory.E)).isEqualTo(id);
        TransactionTemplate tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        tx.executeWithoutResult(s -> {
            EntityManager em=context.getBean("entityManager", EntityManager.class);
            em.createQuery("update User u set u.status = :status where u.id = :id")
                    .setParameter("status", UserStatus.INACTIVE).setParameter("id", userId).executeUpdate();
        });
        assertThat(store.prepare(userId, DAY)).isEmpty();
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.CANCELLED);
    }
    @Test void afterCommitEventPersistsOutcomeInNewTransactions() {
        when(sender.sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new PushDeliveryResult(1, 0, false, true));
        // B is exempt from quiet hours, so this test is independent of execution clock.
        Long id=store.enqueue(userId, "live-event", NotificationCategory.B, NotificationEvent.ACCEPTANCE,
                "title", "body", Map.of("destination", "HOME"), DAY);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.PUSH_SENT);
        verify(sender, times(1)).sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap());
    }
    @Test void unavailablePushIsPendingAlimtalkRatherThanSent() {
        when(sender.sendToUserWithResult(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new PushDeliveryResult(0, 0, true, true));
        Long id=store.enqueue(userId, "denied", NotificationCategory.B, NotificationEvent.SEND_FAILURE,
                "title", "body", Map.of("destination", "HOME"), DAY);
        NotificationDispatch result=repository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(NotificationDispatch.Status.ALIMTALK_PENDING);
        assertThat(result.getDeliveryReason()).isEqualTo("PERMISSION_DENIED");
    }
    @Test void sourceCancellationReleasesDailySlotBeforeSend() {
        Long cancelled = enqueue("cancel-before-send", NotificationCategory.A);
        Long deliverable = enqueue("still-valid", NotificationCategory.E);
        assertThat(store.prepare(userId, DAY)).containsExactly(deliverable);
        assertThat(repository.findById(cancelled).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.CANCELLED);
    }

    @Test void cancelledReadyReservationDoesNotSuppressAnotherQueuedNotification() {
        NotificationDispatch invalid=new NotificationDispatch(userId, "cancel-before-send", NotificationCategory.A,
                NotificationEvent.SCHEDULED, "title", "body", "{}", DAY);
        invalid.select(DAY);
        repository.saveAndFlush(invalid);
        Long valid=enqueue("valid-after-cancel", NotificationCategory.E);
        assertThat(store.prepare(userId, DAY)).containsExactly(valid);
        assertThat(repository.findById(invalid.getId()).orElseThrow().getStatus())
                .isEqualTo(NotificationDispatch.Status.CANCELLED);
    }

    @Test void startupKeepsQueuedSendsUntilDataUpgradeHasCompleted() {
        var readiness=new NotificationReadiness();
        var worker=context.getBean(NotificationPolicyWorker.class);
        org.springframework.test.util.ReflectionTestUtils.setField(worker,"readiness",readiness);
        when(sender.sendToUserWithResult(anyLong(),anyString(),anyString(),anyMap())).thenReturn(new PushDeliveryResult(1,0,false,true));
        Long id=enqueue("startup-gated",NotificationCategory.E);worker.poll();
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.QUEUED);
        verifyNoInteractions(sender);
        readiness.onReady(null);worker.poll();
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationDispatch.Status.PUSH_SENT);
    }
    @Configuration(proxyBeanMethods=false)
    @EnableTransactionManagement(proxyTargetClass=true)
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackages={"com.afternote.domain.user.repository", "com.afternote.domain.notification.policy"})
    @Import({NotificationPolicyStore.class, NotificationPolicyWorker.class})
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(ds);
            factory.setPackagesToScan("com.afternote.domain"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties props=new Properties(); props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            factory.setJpaProperties(props); return factory;
        }
        @Bean EntityManager entityManager(EntityManagerFactory factory) { return SharedEntityManagerCreator.createSharedEntityManager(factory); }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean Clock clock() { return Clock.fixed(DAY.atZone(NotificationTiming.ZONE).toInstant(), NotificationTiming.ZONE); }
        @Bean NotificationDispatchGuard deliveryGuard() { return (notification, now) -> !notification.getEventKey().equals("cancel-before-send"); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean FcmPushSender sender() { return mock(FcmPushSender.class); }
    }
}
