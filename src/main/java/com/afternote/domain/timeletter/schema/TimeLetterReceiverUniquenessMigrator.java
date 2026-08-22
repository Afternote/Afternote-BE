package com.afternote.domain.timeletter.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;

/**
 * 기존 타임레터-수신자 중복 관계를 정리하고 MySQL 유일 제약을 보장한다.
 *
 * <p>Hibernate 스키마 갱신은 ApplicationRunner보다 먼저 실행된다. 엔티티에 유일 제약을 선언하면
 * 레거시 중복 행이 있는 DB는 정리 전에 기동이 실패하므로, 데이터 정리와 제약 추가를 이 마이그레이터가
 * 같은 임계 구역에서 수행한다.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class TimeLetterReceiverUniquenessMigrator implements ApplicationRunner {

    static final String UNIQUE_CONSTRAINT_NAME = "uk_time_letter_receiver_pair";

    private static final String MIGRATION_LOCK_NAME =
            "afternote_time_letter_receiver_uniqueness_v1";
    private static final String TEMPORARY_TABLE_NAME =
            "tmp_time_letter_receiver_dedup";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        if (!isMysql()) {
            return;
        }

        try {
            jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
                migrate(connection);
                return null;
            });
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "Failed to enforce time-letter receiver uniqueness", e
            );
        }
    }

    private void migrate(Connection connection) throws SQLException {
        if (!tableExists(connection)) {
            return;
        }

        boolean lockAcquired = false;
        Throwable failure = null;
        try {
            acquireMigrationLock(connection);
            lockAcquired = true;

            if (hasCompatibleUniqueIndex(connection)) {
                return;
            }

            dropTemporaryTable(connection);
            createDuplicateSnapshot(connection);
            int survivorUpdates = mergeDuplicateState(connection);
            int deletedRows = deleteDuplicateRows(connection);
            dropTemporaryTable(connection);

            try {
                addUniqueConstraint(connection);
            } catch (SQLException e) {
                if (!hasCompatibleUniqueIndex(connection)) {
                    throw e;
                }
            }
            log.info(
                    "[TimeLetterSchema] added {} after updating {} survivors and deleting {} duplicates",
                    UNIQUE_CONSTRAINT_NAME,
                    survivorUpdates,
                    deletedRows
            );
        } catch (SQLException e) {
            failure = e;
            throw e;
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            SQLException cleanupFailure = null;
            try {
                dropTemporaryTable(connection);
            } catch (SQLException e) {
                cleanupFailure = e;
            }

            if (lockAcquired) {
                try {
                    releaseMigrationLock(connection);
                } catch (SQLException e) {
                    if (cleanupFailure == null) {
                        cleanupFailure = e;
                    } else {
                        cleanupFailure.addSuppressed(e);
                    }
                }
            }

            if (cleanupFailure != null) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private boolean isMysql() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String product = metaData.getDatabaseProductName();
            return product != null && product.toLowerCase().contains("mysql");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to detect database product", e);
        }
    }

    private boolean tableExists(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                        SELECT COUNT(*)
                        FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'time_letter_receiver'
                        """
        ); ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    private boolean hasCompatibleUniqueIndex(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
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
                        """
        ); ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() && resultSet.getInt(1) > 0;
        }
    }

    private void acquireMigrationLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT GET_LOCK(?, 60)"
        )) {
            statement.setString(1, MIGRATION_LOCK_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getObject(1) == null || resultSet.getInt(1) != 1) {
                    throw new SQLException("Timed out acquiring time-letter receiver migration lock");
                }
            }
        }
    }

    private void releaseMigrationLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT RELEASE_LOCK(?)"
        )) {
            statement.setString(1, MIGRATION_LOCK_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next() || resultSet.getObject(1) == null || resultSet.getInt(1) != 1) {
                    throw new SQLException("Failed to release time-letter receiver migration lock");
                }
            }
        }
    }

    private void createDuplicateSnapshot(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    """
                            CREATE TEMPORARY TABLE tmp_time_letter_receiver_dedup ENGINE=InnoDB AS
                            SELECT MIN(id) AS keep_id,
                                   time_letter_id,
                                   receiver_id,
                                   MIN(created_at) AS created_at,
                                   MIN(delivered_at) AS delivered_at,
                                   MIN(read_at) AS read_at
                            FROM time_letter_receiver
                            GROUP BY time_letter_id, receiver_id
                            HAVING COUNT(*) > 1
                            """
            );
        }
    }

    private int mergeDuplicateState(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(
                    """
                            UPDATE time_letter_receiver survivor
                            JOIN tmp_time_letter_receiver_dedup duplicate_group
                              ON duplicate_group.keep_id = survivor.id
                            SET survivor.created_at = duplicate_group.created_at,
                                survivor.delivered_at = duplicate_group.delivered_at,
                                survivor.read_at = duplicate_group.read_at
                            """
            );
        }
    }

    private int deleteDuplicateRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(
                    """
                            DELETE duplicate_row
                            FROM time_letter_receiver duplicate_row
                            JOIN tmp_time_letter_receiver_dedup duplicate_group
                              ON duplicate_group.time_letter_id = duplicate_row.time_letter_id
                             AND duplicate_group.receiver_id = duplicate_row.receiver_id
                            WHERE duplicate_row.id <> duplicate_group.keep_id
                            """
            );
        }
    }

    private void addUniqueConstraint(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "ALTER TABLE time_letter_receiver ADD CONSTRAINT "
                            + UNIQUE_CONSTRAINT_NAME
                            + " UNIQUE (time_letter_id, receiver_id)"
            );
        }
    }

    private void dropTemporaryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TEMPORARY TABLE IF EXISTS " + TEMPORARY_TABLE_NAME);
        }
    }
}
