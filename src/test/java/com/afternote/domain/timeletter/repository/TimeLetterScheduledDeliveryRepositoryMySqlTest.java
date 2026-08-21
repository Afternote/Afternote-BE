package com.afternote.domain.timeletter.repository;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("예약 타임레터 발송 처리 MySQL 통합 테스트")
class TimeLetterScheduledDeliveryRepositoryMySqlTest {

    private static final LocalDateTime TRANSITIONED_AT =
            LocalDateTime.of(2026, 8, 21, 12, 0, 0, 123_456_000);

    private MySQLContainer<?> mysql;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private TimeLetterScheduledDeliveryRepository repository;
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

        jdbcTemplate = new JdbcTemplate(dataSource);
        indexMigrator = new TimeLetterScheduleIndexMigrator(jdbcTemplate, dataSource);
    }

    @AfterAll
    void stopMysql() {
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
                    delivered_at DATETIME(6) NULL,
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                ) ENGINE=InnoDB
                """);
        repository = new TimeLetterScheduledDeliveryRepository(jdbcTemplate, dataSource);
        repository.initializeSchemaCompatibility();
    }

    @Test
    @DisplayName("발송할 타임레터가 없으면 아무 데이터도 변경하지 않는다")
    void noDueDateLettersLeavesEveryRowUntouched() {
        insert(1, "SCHEDULED", "DATE", TRANSITIONED_AT.plusMinutes(1), null);
        insert(2, "SCHEDULED", "POST_DEATH", TRANSITIONED_AT.minusMinutes(1), null);
        insert(3, "DRAFT", "DATE", TRANSITIONED_AT.minusMinutes(1), null);

        int updated = repository.markDueDateLettersAsSent(TRANSITIONED_AT);

        assertThat(updated).isZero();
        assertThat(countByStatus("SCHEDULED")).isEqualTo(2);
        assertThat(countByStatus("DRAFT")).isOne();
        assertThat(countDelivered()).isZero();
    }

    @Test
    @DisplayName("발송 시각이 지난 SCHEDULED DATE 타임레터만 한 번에 SENT로 변경한다")
    void transitionsOnlyDueScheduledDateLettersInOneStatement() {
        insert(1, "SCHEDULED", "DATE", TRANSITIONED_AT.minusMinutes(2), null);
        insert(2, "SCHEDULED", "DATE", TRANSITIONED_AT.minusSeconds(1), null);
        insert(3, "SCHEDULED", "DATE", TRANSITIONED_AT.plusSeconds(1), null);
        insert(4, "SCHEDULED", "POST_DEATH", TRANSITIONED_AT.minusDays(1), null);
        insert(5, "DRAFT", "DATE", TRANSITIONED_AT.minusDays(1), null);

        int updated = repository.markDueDateLettersAsSent(TRANSITIONED_AT);

        assertThat(updated).isEqualTo(2);
        assertThat(statusOf(1)).isEqualTo("SENT");
        assertThat(statusOf(2)).isEqualTo("SENT");
        assertThat(statusOf(3)).isEqualTo("SCHEDULED");
        assertThat(statusOf(4)).isEqualTo("SCHEDULED");
        assertThat(statusOf(5)).isEqualTo("DRAFT");
        assertThat(deliveredAtOf(1)).isEqualTo(TRANSITIONED_AT);
        assertThat(deliveredAtOf(2)).isEqualTo(TRANSITIONED_AT);
        assertThat(countDelivered()).isEqualTo(2);
    }

    @Test
    @DisplayName("레거시 delivered_at 컬럼 제거 후에도 SENT 상태로 변경한다")
    void transitionsAfterTheLegacyDeliveredAtColumnIsRemoved() {
        jdbcTemplate.execute("ALTER TABLE time_letters DROP COLUMN delivered_at");
        repository = new TimeLetterScheduledDeliveryRepository(jdbcTemplate, dataSource);
        repository.initializeSchemaCompatibility();
        insertWithoutDeliveredAt(1, "SCHEDULED", "DATE", TRANSITIONED_AT.minusMinutes(1));

        int updated = repository.markDueDateLettersAsSent(TRANSITIONED_AT);

        assertThat(updated).isOne();
        assertThat(statusOf(1)).isEqualTo("SENT");
    }

    @Test
    @DisplayName("동시에 실행해도 각 타임레터를 정확히 한 번만 SENT 상태로 변경한다")
    void concurrentRunsTransitionEachLetterExactlyOnce() throws Exception {
        int letterCount = 20;
        for (long id = 1; id <= letterCount; id++) {
            insert(id, "SCHEDULED", "DATE", TRANSITIONED_AT.minusMinutes(1), null);
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
                    return repository.markDueDateLettersAsSent(TRANSITIONED_AT);
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
        assertThat(countDelivered()).isEqualTo(letterCount);
    }

    @Test
    @DisplayName("예약 발송 대상 조회용 인덱스는 여러 번 적용해도 중복되지 않고 조회 범위를 줄인다")
    void migrationIsIdempotentAndChangesThePlanFromTableScanToIndexRange() {
        List<ScheduleRow> rows = new ArrayList<>();
        rows.add(new ScheduleRow(1, TRANSITIONED_AT.minusMinutes(2)));
        rows.add(new ScheduleRow(2, TRANSITIONED_AT.minusMinutes(1)));
        for (long id = 3; id <= 2_002; id++) {
            rows.add(new ScheduleRow(id, TRANSITIONED_AT.plusDays(1).plusSeconds(id)));
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
        assertThat(after.get("key")).isEqualTo(TimeLetterScheduleIndexMigrator.INDEX_NAME);
        assertThat(((Number) after.get("rows")).longValue())
                .isLessThan(((Number) before.get("rows")).longValue());
        assertThat(indexColumns()).isEqualTo("status,send_at");
    }

    private boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void insert(long id, String status, String deliveryMode, LocalDateTime sendAt,
                        LocalDateTime deliveredAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO time_letters
                            (id, status, delivery_mode, send_at, delivered_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                id,
                status,
                deliveryMode,
                sendAt,
                deliveredAt
        );
    }

    private void insertWithoutDeliveredAt(long id, String status, String deliveryMode,
                                          LocalDateTime sendAt) {
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

    private long countDelivered() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM time_letters WHERE delivered_at IS NOT NULL", Long.class
        );
    }

    private String statusOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM time_letters WHERE id = ?", String.class, id
        );
    }

    private LocalDateTime deliveredAtOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT delivered_at FROM time_letters WHERE id = ?", LocalDateTime.class, id
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
                TRANSITIONED_AT
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
                TimeLetterScheduleIndexMigrator.INDEX_NAME
        );
    }

    private record ScheduleRow(long id, LocalDateTime sendAt) {
    }
}
