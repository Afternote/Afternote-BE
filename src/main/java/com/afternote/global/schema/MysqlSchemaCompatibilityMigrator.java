package com.afternote.global.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Hibernate ddl-auto=update 가 놓치는 MySQL 제약을 기동 시 보정한다.
 * <ul>
 *   <li>기존 ENUM 컬럼 → VARCHAR (Java enum 값 추가가 INSERT 500 으로 이어지지 않게)</li>
 *   <li>CHECK 제약 제거 (Hibernate 6 가 VARCHAR enum 에 만든 IN (...) 도 값을 갱신하지 않음)</li>
 *   <li>emotions.emotion_category NULL 허용 (#139)</li>
 *   <li>time_letter_receiver.delivered_at NULL 허용 (#94)</li>
 *   <li>중복된 time_letters.delivered_at 제거 (#94)</li>
 *   <li>users 마케팅 동의 컬럼 tinyint(1) NOT NULL DEFAULT 0</li>
 *   <li>afternote.category_type NOT NULL (#240)</li>
 *   <li>diary.entry_date DATE NOT NULL, 기존 행은 created_at 날짜로 백필 (#244)</li>
 * </ul>
 * 실패하면 기동을 중단한다. 조용히 삼키면 배포는 성공하고 런타임만 500 이 난다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class MysqlSchemaCompatibilityMigrator implements ApplicationRunner {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");
    private static final int MIN_VARCHAR_LENGTH = 32;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        if (!isMysql()) {
            return;
        }
        try {
            convertEnumColumnsToVarchar();
            dropCheckConstraints();
            ensureEmotionCategoryNullable();
            boolean deliveredAtBecameNullable = ensureTimeLetterReceiverDeliveredAtNullable();
            if (deliveredAtBecameNullable) {
                clearLegacyPostDeathDeliverySchedules();
            }
            boolean legacyTimeLetterDeliveredAtExists =
                    columnExists("time_letters", "delivered_at");
            // 레거시 스키마 전환 때만 데이터를 정규화한다. 정상 운영 중 불일치를 기동 시 복구하지 않는다.
            if (deliveredAtBecameNullable || legacyTimeLetterDeliveredAtExists) {
                normalizeTimeLetterReceiverDeliveredAt(legacyTimeLetterDeliveredAtExists);
            }
            if (legacyTimeLetterDeliveredAtExists) {
                dropLegacyTimeLetterDeliveredAt();
            }
            ensureMarketingConsentColumns();
            ensureAfternoteCategoryTypeNotNull();
            ensureDiaryEntryDate();
        } catch (RuntimeException e) {
            throw new IllegalStateException("MySQL schema compatibility migration failed", e);
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

    private void convertEnumColumnsToVarchar() {
        List<EnumColumn> columns = jdbcTemplate.query(
                """
                        SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
                        FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND DATA_TYPE = 'enum'
                        """,
                (rs, rowNum) -> new EnumColumn(
                        rs.getString("TABLE_NAME"),
                        rs.getString("COLUMN_NAME"),
                        rs.getObject("CHARACTER_MAXIMUM_LENGTH") == null
                                ? MIN_VARCHAR_LENGTH
                                : rs.getInt("CHARACTER_MAXIMUM_LENGTH"),
                        "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"))
                )
        );
        for (EnumColumn column : columns) {
            int length = Math.max(column.length(), MIN_VARCHAR_LENGTH);
            String sql = "ALTER TABLE `" + quote(column.table()) + "` MODIFY COLUMN `"
                    + quote(column.column()) + "` VARCHAR(" + length + ") "
                    + (column.nullable() ? "NULL" : "NOT NULL");
            jdbcTemplate.execute(sql);
            log.info("[SchemaCompat] converted {}.{} ENUM → VARCHAR({})",
                    column.table(), column.column(), length);
        }
    }

    private void dropCheckConstraints() {
        List<CheckConstraint> constraints = jdbcTemplate.query(
                """
                        SELECT TABLE_NAME, CONSTRAINT_NAME
                        FROM information_schema.TABLE_CONSTRAINTS
                        WHERE CONSTRAINT_SCHEMA = DATABASE()
                          AND CONSTRAINT_TYPE = 'CHECK'
                        """,
                (rs, rowNum) -> new CheckConstraint(
                        rs.getString("TABLE_NAME"),
                        rs.getString("CONSTRAINT_NAME")
                )
        );
        for (CheckConstraint constraint : constraints) {
            String sql = "ALTER TABLE `" + quote(constraint.table()) + "` DROP CHECK `"
                    + quote(constraint.name()) + "`";
            jdbcTemplate.execute(sql);
            log.info("[SchemaCompat] dropped CHECK {}.{}", constraint.table(), constraint.name());
        }
    }

    private void ensureEmotionCategoryNullable() {
        String nullable = jdbcTemplate.query(
                """
                        SELECT IS_NULLABLE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'emotions'
                          AND COLUMN_NAME = 'emotion_category'
                        """,
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (nullable == null || "YES".equalsIgnoreCase(nullable)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE emotions MODIFY COLUMN emotion_category VARCHAR(30) NULL");
        log.info("[SchemaCompat] altered emotions.emotion_category to NULL");
    }

    private boolean ensureTimeLetterReceiverDeliveredAtNullable() {
        String nullable = jdbcTemplate.query(
                """
                        SELECT IS_NULLABLE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'time_letter_receiver'
                          AND COLUMN_NAME = 'delivered_at'
                        """,
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (nullable == null || "YES".equalsIgnoreCase(nullable)) {
            return false;
        }
        jdbcTemplate.execute(
                "ALTER TABLE time_letter_receiver MODIFY COLUMN delivered_at DATETIME(6) NULL"
        );
        log.info("[SchemaCompat] altered time_letter_receiver.delivered_at to NULL");
        return true;
    }

    /**
     * NULL 전환 전 POST_DEATH 행에 저장될 수 있었던 값은 실제 전달 시각이 아니라 레거시 예정값이다.
     * 최초 스키마 전환 때만 제거하고, 아래 정규화에서 충족된 조건의 실제 시각을 다시 채운다.
     */
    private void clearLegacyPostDeathDeliverySchedules() {
        int clearedCount = jdbcTemplate.update(
                """
                        UPDATE time_letter_receiver tlr
                        JOIN time_letters tl ON tl.id = tlr.time_letter_id
                        SET tlr.delivered_at = NULL
                        WHERE tl.delivery_mode = 'POST_DEATH'
                          AND tlr.delivered_at IS NOT NULL
                        """
        );
        if (clearedCount > 0) {
            log.info("[SchemaCompat] cleared {} legacy POST_DEATH delivery schedules", clearedCount);
        }
    }

    /**
     * 과거에는 수신자 연결의 delivered_at에 예약 시각(send_at)을 미리 저장했다.
     * 이제 실제 전달 완료 시각만 저장하므로 기존 데이터를 같은 의미로 정규화한다.
     */
    private void normalizeTimeLetterReceiverDeliveredAt(boolean legacyTimeLetterDeliveredAtExists) {
        int pendingDateCount = jdbcTemplate.update(
                """
                        UPDATE time_letter_receiver tlr
                        JOIN time_letters tl ON tl.id = tlr.time_letter_id
                        SET tlr.delivered_at = NULL
                        WHERE tl.delivery_mode = 'DATE'
                          AND tl.status <> 'SENT'
                          AND tlr.delivered_at IS NOT NULL
                        """
        );
        int sentDateCount = legacyTimeLetterDeliveredAtExists
                ? jdbcTemplate.update(
                        """
                                UPDATE time_letter_receiver tlr
                                JOIN time_letters tl ON tl.id = tlr.time_letter_id
                                SET tlr.delivered_at = tl.delivered_at
                                WHERE tl.delivery_mode = 'DATE'
                                  AND tl.status = 'SENT'
                                  AND tl.delivered_at IS NOT NULL
                                  AND (tlr.delivered_at IS NULL OR tlr.delivered_at <> tl.delivered_at)
                                """
                )
                : 0;
        int postDeathReceiverCount = jdbcTemplate.update(
                """
                        UPDATE time_letter_receiver tlr
                        JOIN time_letters tl ON tl.id = tlr.time_letter_id
                        JOIN delivery_condition dc
                          ON dc.receiver_id = tlr.receiver_id
                         AND dc.content_type = 'TIME_LETTER'
                         AND dc.state = 'FULFILLED'
                        SET tlr.delivered_at = dc.fulfilled_at
                        WHERE tl.delivery_mode = 'POST_DEATH'
                          AND tl.status <> 'DRAFT'
                          AND dc.fulfilled_at IS NOT NULL
                          AND (tlr.delivered_at IS NULL OR tlr.delivered_at <> dc.fulfilled_at)
                        """
        );
        int postDeathLetterCount = jdbcTemplate.update(
                """
                        UPDATE time_letters tl
                        JOIN (
                            SELECT DISTINCT tlr.time_letter_id
                            FROM time_letter_receiver tlr
                            JOIN delivery_condition dc
                              ON dc.receiver_id = tlr.receiver_id
                             AND dc.content_type = 'TIME_LETTER'
                             AND dc.state = 'FULFILLED'
                            WHERE tlr.delivered_at IS NOT NULL
                        ) delivered ON delivered.time_letter_id = tl.id
                        SET tl.status = 'SENT'
                        WHERE tl.delivery_mode = 'POST_DEATH'
                          AND tl.status <> 'DRAFT'
                        """
        );

        if (pendingDateCount + sentDateCount + postDeathReceiverCount + postDeathLetterCount > 0) {
            log.info(
                    "[SchemaCompat] normalized time-letter delivery timestamps "
                            + "(pendingDate={}, sentDate={}, postDeathReceiver={}, postDeathLetter={})",
                    pendingDateCount,
                    sentDateCount,
                    postDeathReceiverCount,
                    postDeathLetterCount
            );
        }
    }

    private void dropLegacyTimeLetterDeliveredAt() {
        jdbcTemplate.execute("ALTER TABLE time_letters DROP COLUMN delivered_at");
        log.info("[SchemaCompat] dropped legacy time_letters.delivered_at");
    }

    /**
     * 생성/조회 경로가 category 를 필수로 다루므로 컬럼도 NOT NULL 로 맞춘다.
     * 기존 NULL 행이 있으면 기동을 실패시켜 수동 보정을 강제한다.
     */
    private void ensureAfternoteCategoryTypeNotNull() {
        String nullable = jdbcTemplate.query(
                """
                        SELECT IS_NULLABLE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'afternote'
                          AND COLUMN_NAME = 'category_type'
                        """,
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (nullable == null || "NO".equalsIgnoreCase(nullable)) {
            return;
        }

        Integer nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM afternote WHERE category_type IS NULL",
                Integer.class
        );
        if (nullCount != null && nullCount > 0) {
            throw new IllegalStateException(
                    "afternote.category_type has " + nullCount
                            + " NULL row(s); cannot apply NOT NULL (#240)");
        }

        jdbcTemplate.execute(
                "ALTER TABLE afternote MODIFY COLUMN category_type VARCHAR(20) NOT NULL"
        );
        log.info("[SchemaCompat] altered afternote.category_type to NOT NULL");
    }

    /**
     * 기존 users 행에 NOT NULL 컬럼을 붙일 때 DEFAULT 가 없으면 ALTER 가 실패한다.
     * Hibernate ddl-auto 가 놓치면 여기서 tinyint(1) NOT NULL DEFAULT 0 을 보장한다.
     */
    private void ensureMarketingConsentColumns() {
        ensureTinyintNotNullDefaultFalse("users", "marketing_sms_enabled");
        ensureTinyintNotNullDefaultFalse("users", "marketing_email_enabled");
        ensureTinyintNotNullDefaultFalse("users", "marketing_push_enabled");
    }

    /**
     * 기록일 컬럼. Hibernate ddl-auto 는 기존 행에 NOT NULL DATE 를 바로 붙이지 못하므로
     * NULL 로 추가 → created_at 날짜 백필 → NOT NULL 순으로 보정한다 (#244).
     */
    private void ensureDiaryEntryDate() {
        if (!columnExists("diary", "entry_date")) {
            jdbcTemplate.execute("ALTER TABLE `diary` ADD COLUMN `entry_date` date null");
            jdbcTemplate.update(
                    "UPDATE diary SET entry_date = DATE(created_at) WHERE entry_date IS NULL AND created_at IS NOT NULL"
            );
            jdbcTemplate.execute("ALTER TABLE `diary` MODIFY COLUMN `entry_date` date not null");
            log.info("[SchemaCompat] added diary.entry_date from created_at (#244)");
            return;
        }

        Integer nullCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM diary WHERE entry_date IS NULL",
                Integer.class
        );
        if (nullCount != null && nullCount > 0) {
            jdbcTemplate.update(
                    "UPDATE diary SET entry_date = DATE(created_at) WHERE entry_date IS NULL AND created_at IS NOT NULL"
            );
        }

        String nullable = jdbcTemplate.query(
                """
                        SELECT IS_NULLABLE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = 'diary'
                          AND COLUMN_NAME = 'entry_date'
                        """,
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (nullable == null || "NO".equalsIgnoreCase(nullable)) {
            return;
        }

        Integer remainingNulls = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM diary WHERE entry_date IS NULL",
                Integer.class
        );
        if (remainingNulls != null && remainingNulls > 0) {
            throw new IllegalStateException(
                    "diary.entry_date has " + remainingNulls
                            + " NULL row(s); cannot apply NOT NULL (#244)");
        }

        jdbcTemplate.execute("ALTER TABLE `diary` MODIFY COLUMN `entry_date` date not null");
        log.info("[SchemaCompat] altered diary.entry_date to NOT NULL");
    }

    private void ensureTinyintNotNullDefaultFalse(String table, String column) {
        if (columnExists(table, column)) {
            return;
        }
        jdbcTemplate.execute(
                "ALTER TABLE `" + quote(table) + "` ADD COLUMN `" + quote(column)
                        + "` tinyint(1) not null default 0"
        );
        log.info("[SchemaCompat] added {}.{} tinyint(1) not null default 0", table, column);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE()
                          AND TABLE_NAME = ?
                          AND COLUMN_NAME = ?
                        """,
                Integer.class,
                table,
                column
        );
        return count != null && count > 0;
    }

    static String quote(String identifier) {
        if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return identifier;
    }

    private record EnumColumn(String table, String column, int length, boolean nullable) {
    }

    private record CheckConstraint(String table, String name) {
    }
}
