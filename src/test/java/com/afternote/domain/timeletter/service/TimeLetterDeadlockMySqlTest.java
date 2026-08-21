package com.afternote.domain.timeletter.service;

import com.afternote.domain.delivery.model.ConditionState;
import com.afternote.domain.delivery.model.DeliveryCondition;
import com.afternote.domain.delivery.model.DeliveryContentType;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.delivery.service.DeliveryConditionService;
import com.afternote.domain.delivery.service.DeliveryNotificationService;
import com.afternote.domain.image.service.S3Service;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.model.TimeLetterReceiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.receiver.repository.TimeLetterReceiverRepository;
import com.afternote.domain.receiver.service.ReceivedService;
import com.afternote.domain.timeletter.dto.request.TimeLetterUpdateRequest;
import com.afternote.domain.timeletter.model.*;
import com.afternote.domain.timeletter.repository.TimeLetterRepository;
import com.afternote.domain.user.model.DeliveryConditionType;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.global.config.JpaConfig;
import com.afternote.global.exception.CustomException;
import com.afternote.global.exception.ErrorCode;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@Testcontainers
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.sql.init.mode=never",
        "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        JpaConfig.class,
        TimeLetterService.class,
        TimeLetterDeliveryService.class,
        DeliveryConditionService.class,
        TimeLetterDeadlockMySqlTest.LockProbeConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TimeLetterDeadlockMySqlTest {

    private static final String LOCK_ONE = "findByIdForUpdate";
    private static final String LOCK_ALL = "findAllByIdInOrderByIdForUpdate";
    private static final long WAIT_SECONDS = 15;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("afternote")
            .withUsername("afternote")
            .withPassword("afternote")
            .withCommand("--innodb-lock-wait-timeout=5");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }

    @Autowired
    private TimeLetterService timeLetterService;

    @Autowired
    private DeliveryConditionService deliveryConditionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReceiverRepository receiverRepository;

    @Autowired
    private TimeLetterRepository timeLetterRepository;

    @Autowired
    private TimeLetterReceiverRepository timeLetterReceiverRepository;

    @Autowired
    private DeliveryConditionRepository deliveryConditionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ReceiverRepositoryLockProbe lockProbe;

    @MockBean
    private ReceivedService receivedService;

    @MockBean
    private S3Service s3Service;

    @MockBean
    private ApplicationEventPublisher eventPublisher;

    @MockBean
    private DeliveryNotificationService deliveryNotificationService;

    private ExecutorService executor;
    private TransactionTemplate transactionTemplate;
    private Scenario scenario;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        transactionTemplate = new TransactionTemplate(transactionManager);
        scenario = createScenario();
    }

    @AfterEach
    void tearDown() {
        lockProbe.releaseActiveProbe();
        executor.shutdownNow();
    }

    @Test
    @DisplayName("조건 충족이 먼저 Receiver를 잠그면 타임레터 수정과 교착 없이 직렬화된다")
    void fulfillmentThenUpdateDoesNotDeadlock() throws Exception {
        RaceResult result = runRace(
                LOCK_ONE,
                () -> capture(() -> deliveryConditionService.fulfillByReceiverRequest(
                        scenario.userId(),
                        scenario.receiverId()
                )),
                () -> capture(() -> timeLetterService.updateTimeLetter(
                        scenario.userId(),
                        scenario.timeLetterId(),
                        updateRequest("조건 충족 뒤 수정")
                ))
        );

        assertThat(result.first().failure()).isNull();
        assertThat(result.second().failure())
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TIME_LETTER_ALREADY_SENT));
        assertDeliveredState();
    }

    @Test
    @DisplayName("타임레터 수정이 먼저 Receiver를 잠그면 조건 충족과 교착 없이 직렬화된다")
    void updateThenFulfillmentDoesNotDeadlock() throws Exception {
        RaceResult result = runRace(
                LOCK_ALL,
                () -> capture(() -> timeLetterService.updateTimeLetter(
                        scenario.userId(),
                        scenario.timeLetterId(),
                        updateRequest("조건 충족 전 수정")
                )),
                () -> capture(() -> deliveryConditionService.fulfillByReceiverRequest(
                        scenario.userId(),
                        scenario.receiverId()
                ))
        );

        assertThat(result.first().failure()).isNull();
        assertThat(result.second().failure()).isNull();
        assertDeliveredState();
    }

    private RaceResult runRace(
            String firstReceiverLockMethod,
            Callable<OperationOutcome> firstOperation,
            Callable<OperationOutcome> secondOperation
    ) throws Exception {
        LockControl control = lockProbe.pauseNext(firstReceiverLockMethod);
        Future<OperationOutcome> first = null;
        Future<OperationOutcome> second = null;

        try {
            first = executor.submit(firstOperation);
            assertThat(control.lockAcquired().await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("첫 트랜잭션이 Receiver 잠금을 획득해야 한다")
                    .isTrue();

            second = executor.submit(secondOperation);
            assertThat(control.competingLockAttempted().await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("두 번째 트랜잭션이 같은 Receiver 잠금을 시도해야 한다")
                    .isTrue();

            control.release().countDown();
            return new RaceResult(
                    first.get(WAIT_SECONDS, TimeUnit.SECONDS),
                    second.get(WAIT_SECONDS, TimeUnit.SECONDS)
            );
        } finally {
            control.release().countDown();
            lockProbe.clear(control);
            cancelIfRunning(first);
            cancelIfRunning(second);
        }
    }

    private void assertDeliveredState() {
        DeliveredState state = transactionTemplate.execute(status -> {
            DeliveryCondition condition = deliveryConditionRepository
                    .findByReceiverIdAndContentType(
                            scenario.receiverId(),
                            DeliveryContentType.TIME_LETTER
                    )
                    .orElseThrow();
            TimeLetter timeLetter = timeLetterRepository.findById(scenario.timeLetterId()).orElseThrow();
            TimeLetterReceiver link = timeLetterReceiverRepository
                    .findByTimeLetterId(scenario.timeLetterId())
                    .get(0);
            return new DeliveredState(condition.getState(), timeLetter.getStatus(), link.getDeliveredAt());
        });

        assertThat(state).isNotNull();
        assertThat(state.conditionState()).isEqualTo(ConditionState.FULFILLED);
        assertThat(state.timeLetterStatus()).isEqualTo(TimeLetterStatus.SENT);
        assertThat(state.deliveredAt()).isNotNull();
    }

    private Scenario createScenario() {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            User user = userRepository.save(User.builder()
                    .email("deadlock-" + suffix + "@example.com")
                    .password("password123!")
                    .name("발신자")
                    .status(UserStatus.ACTIVE)
                    .build());

            Receiver receiver = receiverRepository.save(Receiver.builder()
                    .userId(user.getId())
                    .name("수신자")
                    .email("receiver-" + suffix + "@example.com")
                    .build());

            TimeLetter timeLetter = TimeLetter.builder()
                    .user(user)
                    .title("사후 타임레터")
                    .status(TimeLetterStatus.SCHEDULED)
                    .deliveryMode(TimeLetterDeliveryMode.POST_DEATH)
                    .build();
            timeLetter.replaceBlocks(List.of(TimeLetterBlock.builder()
                    .blockType(TimeLetterBlockType.TEXT)
                    .blockOrder(1)
                    .textContent("본문")
                    .build()));
            timeLetter = timeLetterRepository.save(timeLetter);

            timeLetterReceiverRepository.save(TimeLetterReceiver.builder()
                    .timeLetter(timeLetter)
                    .receiver(receiver)
                    .build());

            deliveryConditionRepository.save(DeliveryCondition.builder()
                    .userId(user.getId())
                    .receiverId(receiver.getId())
                    .contentType(DeliveryContentType.TIME_LETTER)
                    .conditionType(DeliveryConditionType.RECEIVER_REQUEST)
                    .build());

            deliveryConditionRepository.flush();
            return new Scenario(user.getId(), receiver.getId(), timeLetter.getId());
        });
    }

    private TimeLetterUpdateRequest updateRequest(String title) {
        return new TimeLetterUpdateRequest(title, null, null, null, null);
    }

    private OperationOutcome capture(Runnable operation) {
        try {
            operation.run();
            return new OperationOutcome(null);
        } catch (Throwable failure) {
            return new OperationOutcome(failure);
        }
    }

    private void cancelIfRunning(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private record Scenario(Long userId, Long receiverId, Long timeLetterId) {
    }

    private record OperationOutcome(Throwable failure) {
    }

    private record RaceResult(OperationOutcome first, OperationOutcome second) {
    }

    private record DeliveredState(
            ConditionState conditionState,
            TimeLetterStatus timeLetterStatus,
            LocalDateTime deliveredAt
    ) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LockProbeConfiguration {

        @Bean
        static ReceiverRepositoryLockProbe receiverRepositoryLockProbe() {
            return new ReceiverRepositoryLockProbe();
        }
    }

    static final class ReceiverRepositoryLockProbe implements BeanPostProcessor, Ordered {

        private final AtomicReference<LockControl> active = new AtomicReference<>();

        LockControl pauseNext(String methodName) {
            LockControl control = new LockControl(methodName);
            if (!active.compareAndSet(null, control)) {
                throw new IllegalStateException("Receiver lock probe is already active");
            }
            return control;
        }

        void clear(LockControl control) {
            active.compareAndSet(control, null);
        }

        void releaseActiveProbe() {
            LockControl control = active.getAndSet(null);
            if (control != null) {
                control.release().countDown();
            }
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (!(bean instanceof ReceiverRepository)) {
                return bean;
            }

            ProxyFactory proxyFactory = new ProxyFactory(bean);
            proxyFactory.addAdvice((MethodInterceptor) this::intercept);
            return proxyFactory.getProxy(bean.getClass().getClassLoader());
        }

        private Object intercept(MethodInvocation invocation) throws Throwable {
            String methodName = invocation.getMethod().getName();
            LockControl control = active.get();
            if (control == null || !isReceiverLockMethod(methodName)) {
                return invocation.proceed();
            }

            if (methodName.equals(control.pauseMethod()) && control.claimed().compareAndSet(false, true)) {
                Object result = invocation.proceed();
                control.owner().set(Thread.currentThread());
                control.lockAcquired().countDown();
                if (!control.release().await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out while pausing the Receiver lock owner");
                }
                return result;
            }

            Thread owner = control.owner().get();
            if (owner != null && owner != Thread.currentThread()) {
                control.competingLockAttempted().countDown();
            }
            return invocation.proceed();
        }

        private boolean isReceiverLockMethod(String methodName) {
            return LOCK_ONE.equals(methodName) || LOCK_ALL.equals(methodName);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    private record LockControl(
            String pauseMethod,
            AtomicBoolean claimed,
            AtomicReference<Thread> owner,
            CountDownLatch lockAcquired,
            CountDownLatch competingLockAttempted,
            CountDownLatch release
    ) {
        private LockControl(String pauseMethod) {
            this(
                    pauseMethod,
                    new AtomicBoolean(),
                    new AtomicReference<>(),
                    new CountDownLatch(1),
                    new CountDownLatch(1),
                    new CountDownLatch(1)
            );
        }
    }
}
