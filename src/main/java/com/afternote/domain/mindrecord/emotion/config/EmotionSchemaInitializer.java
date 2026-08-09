package com.afternote.domain.mindrecord.emotion.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * emotions.emotion_category 가 레거시 NOT NULL 이면 PENDING insert 가 실패한다 (#139).
 * ddl-auto=update 로는 nullable 전환이 안 되므로 기동 시 보정한다.
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class EmotionSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String nullable = jdbcTemplate.query(
                    """
                            SELECT IS_NULLABLE FROM information_schema.COLUMNS
                            WHERE TABLE_SCHEMA = DATABASE()
                              AND TABLE_NAME = 'emotions'
                              AND COLUMN_NAME = 'emotion_category'
                            """,
                    rs -> rs.next() ? rs.getString(1) : null
            );
            if (nullable == null) {
                log.warn("[EmotionSchema] emotions.emotion_category column not found");
                return;
            }
            if ("YES".equalsIgnoreCase(nullable)) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE emotions MODIFY COLUMN emotion_category VARCHAR(30) NULL");
            log.info("[EmotionSchema] altered emotions.emotion_category to NULL");
        } catch (Exception e) {
            log.error("[EmotionSchema] failed to ensure emotion_category nullable", e);
        }
    }
}
