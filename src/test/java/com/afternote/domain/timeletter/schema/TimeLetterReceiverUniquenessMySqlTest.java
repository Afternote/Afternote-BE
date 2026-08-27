package com.afternote.domain.timeletter.schema;

import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("타임레터-수신자 유일성 MySQL 통합 테스트")
class TimeLetterReceiverUniquenessMySqlTest {

    private static final LocalDateTime BASE_TIME =
            LocalDateTime.of(2026, 8, 22, 0, 0, 0, 123_456_000);

    private MySQLContainer<?> mysql;
    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private TimeLetterReceiverUniquenessMigrator migrator;

    @BeforeAll
    void connectToMysql() {
        String externalUrl = System.getenv("AFTERNOTE_MYSQL_TEST_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            String username = System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_USERNAME", "root");
            String password = System.getenv().getOrDefault("AFTERNOTE_MYSQL_TEST_PASSWORD", "");
            dataSource = new DriverManagerDataSource(externalUrl, username, password);
        } else {
            boolean dockerAvailable = dockerAvailable();
            if (runningInCi()) {
                assertTrue(
                        dockerAvailable,
                        "CI에서는 Docker를 제공해 MySQL 회귀 테스트를 실제 실행해야 합니다."
                );
            }
            assumeTrue(dockerAvailable, "Docker가 있어야 MySQL 회귀 테스트를 실행할 수 있습니다.");

            mysql = new MySQLContainer<>("mysql:8.0");
            mysql.start();
            dataSource = new DriverManagerDataSource(
                    mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()
            );
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        migrator = new TimeLetterReceiverUniquenessMigrator(jdbcTemplate, dataSource);
    }

    @AfterAll
    void stopMysql() {
        if (mysql != null) {
            mysql.stop();
        }
    }

    @BeforeEach
    void resetSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS time_letter_receiver");
        jdbcTemplate.execute(
                """
                        CREATE TABLE time_letter_receiver (
                            id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                            time_letter_id BIGINT NOT NULL,
                            receiver_id BIGINT NOT NULL,
                            delivered_at DATETIME(6) NULL,
                            read_at DATETIME(6) NULL,
                            created_at DATETIME(6) NOT NULL
                        ) ENGINE=InnoDB
                        """
        );
    }

    @Test
    @DisplayName("기존 중복 관계를 한 건으로 정리하고 마이그레이션을 다시 실행해도 성공한다")
    void migrationMergesDuplicatesAndAddsUniqueConstraintIdempotently() {
        insert(10L, 1L, 7L, BASE_TIME.plusMinutes(3), BASE_TIME.plusMinutes(4), null);
        insert(11L, 1L, 7L, BASE_TIME.plusMinutes(1), BASE_TIME.plusMinutes(2), BASE_TIME.plusMinutes(5));
        insert(12L, 1L, 7L, BASE_TIME.plusMinutes(2), null, BASE_TIME.plusMinutes(3));
        insert(20L, 2L, 8L, BASE_TIME.plusMinutes(6), null, null);

        migrator.run(null);
        migrator.run(null);

        assertThat(countRows()).isEqualTo(2);
        assertThat(loadState(1L, 7L)).isEqualTo(new LinkState(
                10L,
                BASE_TIME.plusMinutes(1),
                BASE_TIME.plusMinutes(2),
                BASE_TIME.plusMinutes(3)
        ));
        assertThat(countCompatibleUniqueIndexes()).isOne();
        assertThatThrownBy(() -> insert(
                30L, 1L, 7L, BASE_TIME.plusMinutes(7), null, null
        )).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("독립 트랜잭션이 같은 관계를 동시에 추가해도 한 행만 남는다")
    void concurrentInsertsKeepSingleRelation() throws Exception {
        migrator.run(null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<InsertResult> first = executor.submit(
                    () -> insertConcurrently(77L, 88L, ready, start)
            );
            Future<InsertResult> second = executor.submit(
                    () -> insertConcurrently(77L, 88L, ready, start)
            );

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(InsertResult.SUCCESS, InsertResult.DUPLICATE);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(countPair(77L, 88L)).isOne();
    }

    private InsertResult insertConcurrently(
            long timeLetterId,
            long receiverId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET SESSION innodb_lock_wait_timeout = 5");
            }

            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent insert start timed out");
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO time_letter_receiver (
                                time_letter_id, receiver_id, delivered_at, read_at, created_at
                            ) VALUES (?, ?, NULL, NULL, ?)
                            """
            )) {
                statement.setLong(1, timeLetterId);
                statement.setLong(2, receiverId);
                statement.setTimestamp(3, Timestamp.valueOf(BASE_TIME));
                statement.executeUpdate();
                connection.commit();
                return InsertResult.SUCCESS;
            } catch (SQLException e) {
                rollback(connection, e);
                if (e.getErrorCode() == 1062) {
                    return InsertResult.DUPLICATE;
                }
                throw e;
            }
        }
    }

    private void rollback(Connection connection, SQLException failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean runningInCi() {
        String ci = System.getenv("CI");
        return ci != null && !ci.isBlank() && !"false".equalsIgnoreCase(ci);
    }

    private void insert(
            long id,
            long timeLetterId,
            long receiverId,
            LocalDateTime createdAt,
            LocalDateTime deliveredAt,
            LocalDateTime readAt
    ) {
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO time_letter_receiver (
                                id, time_letter_id, receiver_id, created_at, delivered_at, read_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """
            );
            statement.setLong(1, id);
            statement.setLong(2, timeLetterId);
            statement.setLong(3, receiverId);
            statement.setTimestamp(4, Timestamp.valueOf(createdAt));
            setTimestamp(statement, 5, deliveredAt);
            setTimestamp(statement, 6, readAt);
            return statement;
        });
    }

    private void setTimestamp(
            PreparedStatement statement,
            int index,
            LocalDateTime value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    private long countRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM time_letter_receiver", Long.class
        );
    }

    private long countPair(long timeLetterId, long receiverId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM time_letter_receiver
                        WHERE time_letter_id = ? AND receiver_id = ?
                        """,
                Long.class,
                timeLetterId,
                receiverId
        );
    }

    private long countCompatibleUniqueIndexes() {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM (
                            SELECT INDEX_NAME
                            FROM information_schema.STATISTICS
                            WHERE TABLE_SCHEMA = DATABASE()
                              AND TABLE_NAME = 'time_letter_receiver'
                            GROUP BY INDEX_NAME
                            HAVING MAX(NON_UNIQUE) = 0
                               AND GROUP_CONCAT(
                                   COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','
                               ) = 'time_letter_id,receiver_id'
                        ) compatible_unique_indexes
                        """,
                Long.class
        );
    }

    private LinkState loadState(long timeLetterId, long receiverId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT id, created_at, delivered_at, read_at
                        FROM time_letter_receiver
                        WHERE time_letter_id = ? AND receiver_id = ?
                        """,
                (ResultSet resultSet, int rowNumber) -> new LinkState(
                        resultSet.getLong("id"),
                        resultSet.getTimestamp("created_at").toLocalDateTime(),
                        nullableDateTime(resultSet, "delivered_at"),
                        nullableDateTime(resultSet, "read_at")
                ),
                timeLetterId,
                receiverId
        );
    }

    private LocalDateTime nullableDateTime(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private enum InsertResult {
        SUCCESS,
        DUPLICATE
    }

    private record LinkState(
            long id,
            LocalDateTime createdAt,
            LocalDateTime deliveredAt,
            LocalDateTime readAt
    ) {
    }
}
