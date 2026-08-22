package com.afternote.domain.timeletter.repository;

import com.afternote.domain.timeletter.schema.TimeLetterScheduleIndexMigrator;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("예약 타임레터 발송 처리 MySQL 통합 테스트")
class TimeLetterScheduledDeliveryMySqlTest {

    private static final String SCHEDULE_INDEX_NAME = "idx_time_letters_status_send_at";
    private static final LocalDateTime PROCESSED_AT =
            LocalDateTime.of(2026, 8, 21, 12, 0, 0, 123_456_000);

    private MySQLContainer<?> mysql;
    private AnnotationConfigApplicationContext context;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private TimeLetterRepository repository;
    private TransactionTemplate transactionTemplate;
    private TimeLetterScheduleIndexMigrator indexMigrator;

    @BeforeAll
    void connectToMysql() {
        String externalUrl = System.getenv("AFTERNOTE_MYSQL_TEST_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            String username = System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_USERNAME", "root");
            String password = System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_PASSWORD", "");
            dataSource = new DriverManagerDataSource(externalUrl, username, password);
        } else {
            assumeTrue(dockerAvailable(),
                    "Docker 또는 AFTERNOTE_MYSQL_TEST_URL이 있어야 MySQL 회귀 테스트를 실행할 수 있습니다.");
            mysql = new MySQLContainer<>("mysql:8.4");
            mysql.start();
            dataSource = new DriverManagerDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
            );
        }

        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(JpaTestConfiguration.class);
        context.refresh();

        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = context.getBean(TimeLetterRepository.class);
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        indexMigrator = new TimeLetterScheduleIndexMigrator(jdbcTemplate, dataSource);
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
    void resetSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS time_letters");
        jdbcTemplate.execute("""
                CREATE TABLE time_letters (
                    id BIGINT NOT NULL PRIMARY KEY,
                    status VARCHAR(32) NOT NULL,
                    delivery_mode VARCHAR(20) NOT NULL,
                    send_at DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                ) ENGINE=InnoDB
                """);
    }

    @Test
    @DisplayName("발송할 타임레터가 없으면 아무 데이터도 변경하지 않는다")
    void noDueDateLettersLeavesEveryRowUntouched() {
        insert(1, "SCHEDULED", "DATE", PROCESSED_AT.plusMinutes(1));
        insert(2, "SCHEDULED", "POST_DEATH", PROCESSED_AT.minusMinutes(1));
        insert(3, "DRAFT", "DATE", PROCESSED_AT.minusMinutes(1));

        int updated = markDueDateLettersAsSent();

        assertThat(updated).isZero();
        assertThat(countByStatus("SCHEDULED")).isEqualTo(2);
        assertThat(countByStatus("DRAFT")).isOne();
    }

    @Test
    @DisplayName("발송 시각이 지난 날짜 지정 타임레터만 한 번에 발송 완료 처리한다")
    void updatesOnlyDueScheduledDateLetters() {
        insert(1, "SCHEDULED", "DATE", PROCESSED_AT.minusMinutes(2));
        insert(2, "SCHEDULED", "DATE", PROCESSED_AT.minusSeconds(1));
        insert(3, "SCHEDULED", "DATE", PROCESSED_AT.plusSeconds(1));
        insert(4, "SCHEDULED", "POST_DEATH", PROCESSED_AT.minusDays(1));
        insert(5, "DRAFT", "DATE", PROCESSED_AT.minusDays(1));

        int updated = markDueDateLettersAsSent();

        assertThat(updated).isEqualTo(2);
        assertThat(statusOf(1)).isEqualTo("SENT");
        assertThat(statusOf(2)).isEqualTo("SENT");
        assertThat(statusOf(3)).isEqualTo("SCHEDULED");
        assertThat(statusOf(4)).isEqualTo("SCHEDULED");
        assertThat(statusOf(5)).isEqualTo("DRAFT");
        assertThat(updatedAtOf(1)).isEqualTo(PROCESSED_AT);
        assertThat(updatedAtOf(2)).isEqualTo(PROCESSED_AT);
    }

    @Test
    @DisplayName("동시에 실행해도 각 타임레터를 정확히 한 번만 발송 완료 처리한다")
    void concurrentRunsUpdateEachLetterExactlyOnce() throws Exception {
        int letterCount = 20;
        for (long id = 1; id <= letterCount; id++) {
            insert(id, "SCHEDULED", "DATE", PROCESSED_AT.minusMinutes(1));
        }

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return markDueDateLettersAsSent();
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int first = results.get(0).get(10, TimeUnit.SECONDS);
            int second = results.get(1).get(10, TimeUnit.SECONDS);
            assertThat(List.of(first, second)).containsExactlyInAnyOrder(letterCount, 0);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(countByStatus("SENT")).isEqualTo(letterCount);
    }

    @Test
    @DisplayName("예약 발송 대상 조회용 인덱스는 여러 번 적용해도 중복되지 않고 조회 범위를 줄인다")
    void indexCanBeAppliedRepeatedlyAndReducesRowsRead() {
        List<ScheduleRow> rows = new ArrayList<>();
        rows.add(new ScheduleRow(1, PROCESSED_AT.minusMinutes(2)));
        rows.add(new ScheduleRow(2, PROCESSED_AT.minusMinutes(1)));
        for (long id = 3; id <= 2_002; id++) {
            rows.add(new ScheduleRow(id, PROCESSED_AT.plusDays(1).plusSeconds(id)));
        }
        jdbcTemplate.batchUpdate(
                """
                        INSERT INTO time_letters (id, status, delivery_mode, send_at)
                        VALUES (?, 'SCHEDULED', 'DATE', ?)
                        """,
                rows,
                500,
                (PreparedStatement statement, ScheduleRow row) -> {
                    statement.setLong(1, row.id());
                    statement.setTimestamp(2, Timestamp.valueOf(row.sendAt()));
                }
        );

        Map<String, Object> before = explainDueDateLookup();

        indexMigrator.run(null);
        indexMigrator.run(null);
        jdbcTemplate.execute("ANALYZE TABLE time_letters");
        Map<String, Object> after = explainDueDateLookup();

        assertThat(before.get("key")).isNull();
        assertThat(after.get("key")).isEqualTo(SCHEDULE_INDEX_NAME);
        assertThat(((Number) after.get("rows")).longValue())
                .isLessThan(((Number) before.get("rows")).longValue());
        assertThat(indexColumns()).isEqualTo("status,send_at");
    }

    private int markDueDateLettersAsSent() {
        Integer updated = transactionTemplate.execute(
                status -> repository.markDueDateLettersAsSent(PROCESSED_AT)
        );
        return updated == null ? 0 : updated;
    }

    private boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void insert(long id, String status, String deliveryMode, LocalDateTime sendAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO time_letters (id, status, delivery_mode, send_at)
                        VALUES (?, ?, ?, ?)
                        """,
                id,
                status,
                deliveryMode,
                sendAt
        );
    }

    private long countByStatus(String status) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM time_letters WHERE status = ?", Long.class, status
        );
    }

    private String statusOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM time_letters WHERE id = ?", String.class, id
        );
    }

    private LocalDateTime updatedAtOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM time_letters WHERE id = ?", LocalDateTime.class, id
        );
    }

    private Map<String, Object> explainDueDateLookup() {
        return jdbcTemplate.queryForMap(
                """
                        EXPLAIN FORMAT=TRADITIONAL SELECT id
                        FROM time_letters
                        WHERE status = 'SCHEDULED'
                          AND delivery_mode = 'DATE'
                          AND send_at < ?
                        """,
                PROCESSED_AT
        );
    }

    private String indexColumns() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX)
                        FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'time_letters'
                          AND INDEX_NAME = ?
                        GROUP BY INDEX_NAME
                        """,
                String.class,
                SCHEDULE_INDEX_NAME
        );
    }

    private record ScheduleRow(long id, LocalDateTime sendAt) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = TimeLetterRepository.class)
    static class JpaTestConfiguration {

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean factory =
                    new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.afternote.domain");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

            Properties properties = new Properties();
            properties.setProperty("hibernate.hbm2ddl.auto", "none");
            factory.setJpaProperties(properties);
            return factory;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
            return new JpaTransactionManager(entityManagerFactory);
        }
    }
}
