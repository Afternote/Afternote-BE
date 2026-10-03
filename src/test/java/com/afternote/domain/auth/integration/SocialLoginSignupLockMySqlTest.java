package com.afternote.domain.auth.integration;

import com.afternote.domain.auth.dto.SocialLoginRequest;
import com.afternote.domain.auth.dto.SocialLoginResponse;
import com.afternote.domain.auth.dto.SocialUserInfo;
import com.afternote.domain.auth.service.AuthService;
import com.afternote.domain.auth.service.EmailService;
import com.afternote.domain.auth.service.TokenService;
import com.afternote.domain.auth.service.social.SocialLoginFactory;
import com.afternote.domain.auth.service.social.SocialLoginService;
import com.afternote.domain.user.model.AuthProvider;
import com.afternote.domain.user.model.User;
import com.afternote.domain.user.model.UserStatus;
import com.afternote.domain.user.repository.UserRepository;
import com.afternote.domain.user.service.ActivityTouchService;
import com.afternote.domain.user.service.WithdrawalCooldownService;
import com.afternote.global.jwt.JwtTokenProvider;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 신규 소셜 가입 INSERT와 {@code REQUIRES_NEW} 활동 UPDATE가 같은 행에서
 * lock wait 나지 않는지 실제 MySQL 트랜잭션으로 확인한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("소셜 로그인 신규 가입 MySQL 잠금")
class SocialLoginSignupLockMySqlTest {

    private MySQLContainer<?> mysql;
    private AnnotationConfigApplicationContext context;
    private AuthService authService;
    private UserRepository userRepository;
    private JdbcTemplate jdbcTemplate;
    private SocialLoginFactory socialLoginFactory;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeAll
    void connectToMysql() {
        DataSource dataSource;
        String externalUrl = System.getenv("AFTERNOTE_MYSQL_TEST_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            String username = System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_USERNAME", "root");
            String password = System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_PASSWORD", "");
            dataSource = hikari(externalUrl, username, password);
        } else {
            assumeTrue(dockerAvailable(),
                    "Docker 또는 AFTERNOTE_MYSQL_TEST_URL이 있어야 MySQL 회귀 테스트를 실행할 수 있습니다.");
            mysql = new MySQLContainer<>("mysql:8.0");
            mysql.start();
            dataSource = hikari(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        }

        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(JpaTestConfiguration.class);
        context.refresh();

        authService = context.getBean(AuthService.class);
        userRepository = context.getBean(UserRepository.class);
        jdbcTemplate = new JdbcTemplate(dataSource);
        socialLoginFactory = context.getBean(SocialLoginFactory.class);
        jwtTokenProvider = context.getBean(JwtTokenProvider.class);
    }

    @AfterAll
    void stopMysql() {
        if (context != null) {
            context.close();
        }
        if (mysql != null) {
            mysql.stop();
        }
    }

    @BeforeEach
    void stubTokens() {
        given(jwtTokenProvider.generateAccessToken(anyLong())).willReturn("access");
        given(jwtTokenProvider.generateRefreshToken(anyLong())).willReturn("refresh");
        given(jwtTokenProvider.getAccessTokenExpirationSeconds()).willReturn(3600L);
    }

    @Test
    @DisplayName("신규 카카오 가입은 lock wait 없이 커밋되고 lastActiveAt은 INSERT 값이다")
    void newKakaoSignup_CommitsWithoutLockWait() {
        String email = email();
        stubKakao(email);
        long waitsBefore = rowLockWaits();
        LocalDateTime started = LocalDateTime.now().minusSeconds(1);

        SocialLoginResponse response = authService.socialLogin(new SocialLoginRequest("KAKAO", "kakao-token"));

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.getAccessToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(rowLockWaits()).isEqualTo(waitsBefore);

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getLastActiveAt()).isNotNull();
        assertThat(saved.getLastActiveAt()).isAfterOrEqualTo(started);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email=?", Long.class, email
        )).isEqualTo(1L);
    }

    @Test
    @DisplayName("기존 카카오 재로그인은 last_active_at을 갱신한다")
    void existingKakaoLogin_TouchesActivity() {
        String email = email();
        User existing = userRepository.saveAndFlush(User.builder()
                .email(email)
                .name("기존")
                .status(UserStatus.ACTIVE)
                .provider(AuthProvider.KAKAO)
                .build());
        LocalDateTime stale = LocalDateTime.now().minusDays(100);
        jdbcTemplate.update("UPDATE users SET last_active_at=? WHERE id=?", stale, existing.getId());
        stubKakao(email);

        SocialLoginResponse response = authService.socialLogin(new SocialLoginRequest("KAKAO", "kakao-token"));

        assertThat(response.isNewUser()).isFalse();
        LocalDateTime active = jdbcTemplate.queryForObject(
                "SELECT last_active_at FROM users WHERE id=?", LocalDateTime.class, existing.getId()
        );
        assertThat(active).isAfter(stale);
    }

    private void stubKakao(String email) {
        SocialLoginService kakao = mock(SocialLoginService.class);
        given(socialLoginFactory.getService("KAKAO")).willReturn(kakao);
        given(kakao.getUserInfo(any())).willReturn(SocialUserInfo.builder()
                .provider(AuthProvider.KAKAO)
                .providerId("kakao-1")
                .email(email)
                .name("kakao")
                .build());
    }

    private long rowLockWaits() {
        Long waits = jdbcTemplate.queryForObject(
                "SHOW GLOBAL STATUS LIKE 'Innodb_row_lock_waits'",
                (rs, rowNum) -> Long.parseLong(rs.getString("Value"))
        );
        return waits == null ? 0L : waits;
    }

    private static String email() {
        return UUID.randomUUID() + "@example.test";
    }

    private static DataSource hikari(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=2");
        return new HikariDataSource(config);
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaAuditing
    @EnableJpaRepositories(basePackageClasses = UserRepository.class)
    static class JpaTestConfiguration {

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory =
                    new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.afternote.domain");
            HibernateJpaVendorAdapter vendor = new HibernateJpaVendorAdapter();
            vendor.setGenerateDdl(true);
            factory.setJpaVendorAdapter(vendor);
            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            factory.setJpaProperties(properties);
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }

        @Bean
        ActivityTouchService activityTouchService(UserRepository userRepository) {
            return new ActivityTouchService(userRepository);
        }

        @Bean
        SocialLoginFactory socialLoginFactory() {
            return mock(SocialLoginFactory.class);
        }

        @Bean
        TokenService tokenService() {
            return mock(TokenService.class);
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return mock(JwtTokenProvider.class);
        }

        @Bean
        EmailService emailService() {
            return mock(EmailService.class);
        }

        @Bean
        WithdrawalCooldownService withdrawalCooldownService() {
            return mock(WithdrawalCooldownService.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        AuthService authService(
                UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                JwtTokenProvider jwtTokenProvider,
                TokenService tokenService,
                EmailService emailService,
                WithdrawalCooldownService withdrawalCooldownService,
                SocialLoginFactory socialLoginFactory,
                ActivityTouchService activityTouchService
        ) {
            return new AuthService(
                    userRepository,
                    passwordEncoder,
                    jwtTokenProvider,
                    tokenService,
                    emailService,
                    withdrawalCooldownService,
                    socialLoginFactory,
                    activityTouchService
            );
        }
    }
}
