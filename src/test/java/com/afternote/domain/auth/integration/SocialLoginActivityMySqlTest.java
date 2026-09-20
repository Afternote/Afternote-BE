package com.afternote.domain.auth.integration;

import com.afternote.domain.auth.controller.AuthController;
import com.afternote.domain.auth.dto.LoginResponse;
import com.afternote.domain.auth.event.EmailVerificationMailRunner;
import com.afternote.domain.auth.service.AuthService;
import com.afternote.domain.auth.service.EmailService;
import com.afternote.domain.auth.service.TokenService;
import com.afternote.domain.auth.service.social.KakaoLoginService;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.delivery.model.*;
import com.afternote.domain.delivery.repository.DeliveryConditionRepository;
import com.afternote.domain.delivery.service.DeliveryConditionScheduler;
import com.afternote.domain.delivery.service.DeliveryNotificationService;
import com.afternote.domain.receiver.model.Receiver;
import com.afternote.domain.receiver.repository.ReceiverRepository;
import com.afternote.domain.user.model.*;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.user.service.ActivityTouchService;
import com.afternote.domain.user.service.WithdrawalCooldownService;
import com.afternote.global.config.*;
import com.afternote.global.exception.GlobalExceptionHandler;
import com.afternote.global.jwt.JwtAuthenticationFilter;
import com.afternote.global.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Real HTTP -> Kakao response parsing -> Spring transactions -> MySQL/Redis -> scheduler.
 * Only the external Kakao endpoint and SMTP transport are replaced. No test transaction
 * wraps the requests: assertions read committed data using fresh repository/JDBC calls.
 */
@SpringBootTest(classes = SocialLoginActivityMySqlTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=auth-activity-test", "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
                "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=2",
                "jwt.secret=activity-integration-test-secret-at-least-32-bytes",
                "jwt.access-token-expiration=3600000", "jwt.refresh-token-expiration=604800000",
                "spring.mail.username=sender@example.test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = SocialLoginActivityMySqlTest.InfrastructureCleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class SocialLoginActivityMySqlTest {
    private static MySQLContainer<?> mysql;
    private static GenericContainer<?> redis;
    private static HttpServer kakao;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) throws IOException {
        String externalUrl = System.getenv("AFTERNOTE_MYSQL_TEST_URL");
        if (externalUrl == null || externalUrl.isBlank()) {
            mysql = new MySQLContainer<>("mysql:8.0");
            mysql.start();
            properties.add("spring.datasource.url", mysql::getJdbcUrl);
            properties.add("spring.datasource.username", mysql::getUsername);
            properties.add("spring.datasource.password", mysql::getPassword);
        } else {
            properties.add("spring.datasource.url", () -> externalUrl);
            properties.add("spring.datasource.username", () -> System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_USERNAME", "root"));
            properties.add("spring.datasource.password", () -> System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_PASSWORD", ""));
        }
        String redisPort = System.getenv("AFTERNOTE_REDIS_TEST_PORT");
        if (redisPort == null || redisPort.isBlank()) {
            redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            redis.start();
            properties.add("spring.data.redis.host", redis::getHost);
            properties.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        } else {
            properties.add("spring.data.redis.host", () -> "127.0.0.1");
            properties.add("spring.data.redis.port", () -> redisPort);
        }
        kakao = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        kakao.createContext("/v2/user/me", exchange -> {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            boolean valid = authorization != null && authorization.startsWith("Bearer ")
                    && authorization.endsWith("@example.test");
            String body = valid ? "{\"id\":293,\"kakao_account\":{\"email\":\""
                    + authorization.substring(7) + "\",\"profile\":{\"nickname\":\"Kakao test\"}}}" : "{}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(valid ? 200 : 401, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        kakao.start();
        properties.add("kakao.api.user-info-url", () -> "http://127.0.0.1:" + kakao.getAddress().getPort() + "/v2/user/me");
    }

    public static class InfrastructureCleanup extends AbstractTestExecutionListener {
        // afterTestClass runs in reverse order: Spring closes the dirty context before
        // we stop its databases, so JPA can finish create-drop cleanup without timeouts.
        @Override public int getOrder() { return 0; }
        @Override public void afterTestClass(TestContext testContext) {
            if (kakao != null) kakao.stop(0);
            if (redis != null) redis.stop();
            if (mysql != null) mysql.stop();
        }
    }

    @Autowired TestRestTemplate http;
    @Autowired UserRepository users;
    @Autowired ReceiverRepository receivers;
    @Autowired DeliveryConditionRepository conditions;
    @Autowired DeliveryConditionScheduler scheduler;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokens;
    @Autowired JwtTokenProvider jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired PlatformTransactionManager transactions;
    @MockBean JavaMailSender mail;

    @BeforeEach
    void removeConditionsFromPreviousScenario() {
        conditions.deleteAll();
        reset(mail);
    }

    @Test
    void newKakaoSignupCommitsUserActivityAndUsableTokensWithoutLockTimeout() {
        String email = email();
        LocalDateTime started = LocalDateTime.now();
        long waitsBefore = rowLockWaits();
        JsonNode data = successfulPost("/social/login", Map.of("provider", "KAKAO", "accessToken", email));
        assertThat(data.path("isNewUser").asBoolean()).isTrue();
        User user = users.findByEmail(email).orElseThrow();
        assertThat(user.getLastActiveAt()).isAfterOrEqualTo(started);
        assertThat(jdbc.queryForObject("SELECT provider FROM user_providers WHERE user_id=?", String.class, user.getId())).isEqualTo("KAKAO");
        assertTokens(data, user.getId());
        // Check the DB counter, not wall time: slow CI startup must not look like a lock wait.
        assertThat(rowLockWaits()).isEqualTo(waitsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email=?", Long.class, email)).isEqualTo(1L);
    }

    @Test
    void expiredRefreshThenKakaoReloginRestoresPendingConditionAndSendsNoDeliveryMail() {
        User user = existingUser(AuthProvider.KAKAO);
        DeliveryCondition condition = inactiveCondition(user);
        scheduler.evaluateInactivityConditions();
        assertThat(conditions.findById(condition.getId()).orElseThrow().getState()).isEqualTo(ConditionState.PENDING_CONFIRMATION);
        ArgumentCaptor<SimpleMailMessage> confirmation = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(confirmation.capture());
        assertThat(confirmation.getValue().getTo()).containsExactly(user.getEmail());
        assertThat(confirmation.getValue().getText()).contains("7일");
        jdbc.update("UPDATE delivery_condition SET grace_period_started_at=? WHERE id=?", LocalDateTime.now().minusDays(3), condition.getId());
        reset(mail);

        String expired = Jwts.builder().subject(user.getId().toString())
                .expiration(Date.from(Instant.now().minusSeconds(60))).signWith(jwt.getKey()).compact();
        LocalDateTime inactive = activity(user.getId());
        assertThat(http.postForEntity("/api/v1/auth/reissue", Map.of("refreshToken", expired), JsonNode.class)
                .getStatusCode().is4xxClientError()).isTrue();
        assertThat(activity(user.getId())).isEqualTo(inactive);
        LocalDateTime started = LocalDateTime.now();
        JsonNode data = successfulPost("/social/login", Map.of("provider", "KAKAO", "accessToken", user.getEmail()));
        assertThat(data.path("isNewUser").asBoolean()).isFalse();
        assertTokens(data, user.getId());
        assertThat(activity(user.getId())).isAfterOrEqualTo(started);

        scheduler.evaluateInactivityConditions();
        DeliveryCondition restored = conditions.findById(condition.getId()).orElseThrow();
        assertThat(restored.getState()).isEqualTo(ConditionState.ACTIVE);
        assertThat(restored.getGracePeriodStartedAt()).isNull();
        assertThat(restored.getFulfilledAt()).isNull();
        scheduler.evaluateInactivityConditions();
        verifyNoInteractions(mail);
    }

    @Test
    void noReturnAfterGraceExpiresFulfillsAndSendsReceiverMailExactlyOnce() {
        User user = existingUser(AuthProvider.KAKAO);
        DeliveryCondition condition = inactiveCondition(user);
        scheduler.evaluateInactivityConditions();
        jdbc.update("UPDATE delivery_condition SET grace_period_started_at=? WHERE id=?", LocalDateTime.now().minusDays(8), condition.getId());
        reset(mail);
        scheduler.evaluateInactivityConditions();
        DeliveryCondition expired = conditions.findById(condition.getId()).orElseThrow();
        assertThat(expired.getState()).isEqualTo(ConditionState.FULFILLED);
        assertThat(expired.getFulfilledAt()).isNotNull();
        scheduler.evaluateInactivityConditions();
        ArgumentCaptor<SimpleMailMessage> delivered = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, times(1)).send(delivered.capture());
        assertThat(delivered.getValue().getTo()).containsExactly("receiver@example.test");
        assertThat(delivered.getValue().getText()).contains("기록을 이제 열람");
    }

    @Test
    void rejectedKakaoTokenDoesNotTouchActivityOrResetPendingCondition() {
        User user = existingUser(AuthProvider.KAKAO);
        DeliveryCondition condition = inactiveCondition(user);
        scheduler.evaluateInactivityConditions();
        LocalDateTime inactive = activity(user.getId());
        reset(mail);
        var response = http.postForEntity("/api/v1/auth/social/login",
                Map.of("provider", "KAKAO", "accessToken", "rejected"), JsonNode.class);
        assertThat(response.getStatusCode().isError()).isTrue();
        assertThat(activity(user.getId())).isEqualTo(inactive);
        scheduler.evaluateInactivityConditions();
        assertThat(conditions.findById(condition.getId()).orElseThrow().getState()).isEqualTo(ConditionState.PENDING_CONFIRMATION);
        verifyNoInteractions(mail);
    }

    @Test
    void emailLoginAndRefreshStillPersistActivityAndRedisTokens() {
        User user = existingUser(AuthProvider.LOCAL);
        LocalDateTime started = LocalDateTime.now();
        JsonNode login = successfulPost("/login", Map.of("email", user.getEmail(), "password", "password123!"));
        assertTokens(login, user.getId());
        assertThat(activity(user.getId())).isAfterOrEqualTo(started);
        jdbc.update("UPDATE users SET last_active_at=? WHERE id=?", LocalDateTime.now().minusDays(100), user.getId());
        started = LocalDateTime.now();
        JsonNode refresh = successfulPost("/reissue", Map.of("refreshToken", login.path("refreshToken").asText()));
        assertTokens(refresh, user.getId());
        assertThat(activity(user.getId())).isAfterOrEqualTo(started);
    }

    @Test
    void sharedTokenEntryUsedByPasskeysStillPersistsActivityWithinCallerTransaction() {
        User user = existingUser(AuthProvider.LOCAL);
        LocalDateTime started = LocalDateTime.now();
        LoginResponse response = new TransactionTemplate(transactions).execute(status ->
                auth.issueTokens(users.findById(user.getId()).orElseThrow()));
        assertThat(response).isNotNull();
        assertThat(jwt.validateToken(response.getAccessToken())).isTrue();
        assertThat(tokens.getUserId(response.getRefreshToken())).isEqualTo(user.getId());
        assertThat(activity(user.getId())).isAfterOrEqualTo(started);
    }

    private JsonNode successfulPost(String endpoint, Map<String, String> body) {
        ResponseEntity<JsonNode> response = http.postForEntity("/api/v1/auth" + endpoint, body, JsonNode.class);
        assertThat(response.getStatusCode().value()).as("HTTP response: %s", response.getBody()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("code").asInt()).isEqualTo(200);
        return response.getBody().path("data");
    }

    private void assertTokens(JsonNode data, Long userId) {
        String access = data.path("accessToken").asText();
        String refresh = data.path("refreshToken").asText();
        assertThat(jwt.validateToken(access)).isTrue();
        assertThat(jwt.getUserId(access)).isEqualTo(userId);
        assertThat(jwt.validateToken(refresh)).isTrue();
        assertThat(tokens.getUserId(refresh)).isEqualTo(userId);
        assertThat(data.path("expiresIn").asLong()).isEqualTo(3600);
    }

    private User existingUser(AuthProvider provider) {
        User user = users.saveAndFlush(User.builder().email(email()).name("Existing user")
                .provider(provider).status(UserStatus.ACTIVE)
                .password(provider == AuthProvider.LOCAL ? passwords.encode("password123!") : null).build());
        jdbc.update("UPDATE users SET last_active_at=? WHERE id=?", LocalDateTime.now().minusDays(100), user.getId());
        return user;
    }

    private DeliveryCondition inactiveCondition(User user) {
        Receiver receiver = receivers.saveAndFlush(Receiver.builder().userId(user.getId()).name("Receiver")
                .email("receiver@example.test").build());
        return conditions.saveAndFlush(DeliveryCondition.builder().userId(user.getId()).receiverId(receiver.getId())
                .contentType(DeliveryContentType.TIME_LETTER).conditionType(DeliveryConditionType.INACTIVITY)
                .inactivityPeriod(InactivityPeriod.THREE_MONTHS).build());
    }

    private long rowLockWaits() {
        return jdbc.queryForObject("SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_waits'",
                (rs, row) -> Long.parseLong(rs.getString("Value")));
    }

    private LocalDateTime activity(Long userId) {
        return jdbc.queryForObject("SELECT last_active_at FROM users WHERE id=?", LocalDateTime.class, userId);
    }

    private static String email() { return UUID.randomUUID() + "@example.test"; }

    @Configuration(proxyBeanMethods = false)
    @EntityScan("com.afternote.domain")
    @EnableJpaRepositories(basePackageClasses = {UserRepository.class, ReceiverRepository.class, DeliveryConditionRepository.class})
    @ImportAutoConfiguration({PropertyPlaceholderAutoConfiguration.class, ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class, JacksonAutoConfiguration.class,
            ValidationAutoConfiguration.class, DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class, RedisAutoConfiguration.class,
            SecurityAutoConfiguration.class})
    @Import({AuthController.class, AuthService.class, ActivityTouchService.class, WithdrawalCooldownService.class,
            TokenService.class, EmailService.class, EmailVerificationMailRunner.class, SocialLoginFactory.class,
            KakaoLoginService.class, WebClientConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class,
            SecurityConfig.class, RedisConfig.class, JpaConfig.class, GlobalExceptionHandler.class,
            DeliveryConditionScheduler.class, DeliveryNotificationService.class})
    static class Application { }
}
