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
 *   <li>users 마케팅 동의 컬럼 tinyint(1) NOT NULL DEFAULT 0</li>
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
            ensureMarketingConsentColumns();
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

    /**
     * 기존 users 행에 NOT NULL 컬럼을 붙일 때 DEFAULT 가 없으면 ALTER 가 실패한다.
     * Hibernate ddl-auto 가 놓치면 여기서 tinyint(1) NOT NULL DEFAULT 0 을 보장한다.
     */
    private void ensureMarketingConsentColumns() {
        ensureTinyintNotNullDefaultFalse("users", "marketing_sms_enabled");
        ensureTinyintNotNullDefaultFalse("users", "marketing_email_enabled");
        ensureTinyintNotNullDefaultFalse("users", "marketing_push_enabled");
    }

    private void ensureTinyintNotNullDefaultFalse(String table, String column) {
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
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(
                "ALTER TABLE `" + quote(table) + "` ADD COLUMN `" + quote(column)
                        + "` tinyint(1) not null default 0"
        );
        log.info("[SchemaCompat] added {}.{} tinyint(1) not null default 0", table, column);
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
