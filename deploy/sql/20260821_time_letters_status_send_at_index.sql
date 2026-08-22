-- #191: 예약 타임레터 전이 쿼리용 복합 인덱스
-- 실제 배포에서는 TimeLetterScheduleIndexMigrator가 같은 선두 컬럼의 인덱스를 자동 보장한다.
-- 아래 SQL은 운영자가 수동 적용해야 할 때 사용할 수 있으며 반복 실행해도 안전하다.

SET @time_letter_schedule_index_exists = (
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
);

SET @time_letter_schedule_index_ddl = IF(
    @time_letter_schedule_index_exists = 0,
    'CREATE INDEX idx_time_letters_status_send_at ON time_letters (status, send_at)',
    'SELECT 1'
);

PREPARE time_letter_schedule_index_statement FROM @time_letter_schedule_index_ddl;
EXECUTE time_letter_schedule_index_statement;
DEALLOCATE PREPARE time_letter_schedule_index_statement;
