package com.afternote.domain.timeletter.schema;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * 예약 발송 대상 조회가 전체 테이블을 훑지 않도록 MySQL 인덱스를 보장한다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class TimeLetterScheduleIndexMigrator implements ApplicationRunner {

    private static final String INDEX_NAME = "idx_time_letters_status_send_at";

    private static final String FIND_COMPATIBLE_INDEX = """
            SELECT COUNT(DISTINCT first_col.INDEX_NAME)
            FROM information_schema.STATISTICS first_col
            JOIN information_schema.STATISTICS second_col
              ON second_col.TABLE_SCHEMA = first_col.TABLE_SCHEMA
             AND second_col.TABLE_NAME = first_col.TABLE_NAME
             AND second_col.INDEX_NAME = first_col.INDEX_NAME
            WHERE first_col.TABLE_SCHEMA = DATABASE()
              AND first_col.TABLE_NAME = 'time_letters'
              AND first_col.SEQ_IN_INDEX = 1
              AND first_col.COLUMN_NAME = 'status'
              AND second_col.SEQ_IN_INDEX = 2
              AND second_col.COLUMN_NAME = 'send_at'
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        if (!isMysql() || hasCompatibleIndex()) {
            return;
        }

        try {
            jdbcTemplate.execute(
                    "CREATE INDEX " + INDEX_NAME + " ON time_letters (status, send_at)"
            );
        } catch (DataAccessException e) {
            // 롤링 기동 중 다른 인스턴스가 먼저 생성한 경우만 성공으로 취급한다.
            if (hasCompatibleIndex()) {
                return;
            }
            throw e;
        }
        log.info("[TimeLetterSchema] created {}", INDEX_NAME);
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

    private boolean hasCompatibleIndex() {
        Integer count = jdbcTemplate.queryForObject(FIND_COMPATIBLE_INDEX, Integer.class);
        return count != null && count > 0;
    }
}
