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
