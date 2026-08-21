-- #172: 타임레터-수신자 관계를 한 건으로 정규화하고 DB 불변식으로 보장한다.
-- 중복 행이 가진 실제 전달/읽음 시각은 가장 이른 시각으로 합치며 가장 작은 ID를 유지한다.
DROP TEMPORARY TABLE IF EXISTS tmp_time_letter_receiver_dedup;

CREATE TEMPORARY TABLE tmp_time_letter_receiver_dedup ENGINE=InnoDB AS
SELECT MIN(id) AS keep_id,
       time_letter_id,
       receiver_id,
       MIN(created_at) AS created_at,
       MIN(delivered_at) AS delivered_at,
       MIN(read_at) AS read_at
FROM time_letter_receiver
GROUP BY time_letter_id, receiver_id
HAVING COUNT(*) > 1;

UPDATE time_letter_receiver survivor
JOIN tmp_time_letter_receiver_dedup duplicate_group
  ON duplicate_group.keep_id = survivor.id
SET survivor.created_at = duplicate_group.created_at,
    survivor.delivered_at = duplicate_group.delivered_at,
    survivor.read_at = duplicate_group.read_at;

DELETE duplicate_row
FROM time_letter_receiver duplicate_row
JOIN tmp_time_letter_receiver_dedup duplicate_group
  ON duplicate_group.time_letter_id = duplicate_row.time_letter_id
 AND duplicate_group.receiver_id = duplicate_row.receiver_id
WHERE duplicate_row.id <> duplicate_group.keep_id;

DROP TEMPORARY TABLE tmp_time_letter_receiver_dedup;

SET @time_letter_receiver_unique_exists := (
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
);

SET @time_letter_receiver_unique_ddl := IF(
    @time_letter_receiver_unique_exists = 0,
    'ALTER TABLE time_letter_receiver ADD CONSTRAINT uk_time_letter_receiver_pair UNIQUE (time_letter_id, receiver_id)',
    'SELECT 1'
);

PREPARE time_letter_receiver_unique_statement FROM @time_letter_receiver_unique_ddl;
EXECUTE time_letter_receiver_unique_statement;
DEALLOCATE PREPARE time_letter_receiver_unique_statement;
