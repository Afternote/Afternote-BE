-- #94: 수신자 연결 생성 시에는 아직 실제 전달 전이므로 NULL 허용
-- Hibernate ddl-auto=update 는 NOT NULL -> NULL 변경을 하지 않음
SET @time_letter_receiver_delivered_at_was_not_nullable := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'time_letter_receiver'
      AND COLUMN_NAME = 'delivered_at'
      AND IS_NULLABLE = 'NO'
);

ALTER TABLE time_letter_receiver MODIFY COLUMN delivered_at DATETIME(6) NULL;

-- 기존 POST_DEATH 값은 실제 전달이 아닌 레거시 예정값이므로 먼저 제거한다.
UPDATE time_letter_receiver tlr
JOIN time_letters tl ON tl.id = tlr.time_letter_id
SET tlr.delivered_at = NULL
WHERE @time_letter_receiver_delivered_at_was_not_nullable = 1
  AND tl.delivery_mode = 'POST_DEATH'
  AND tlr.delivered_at IS NOT NULL;

-- 기존 DATE 연결에 미리 저장한 예약 시각을 제거한다.
UPDATE time_letter_receiver tlr
JOIN time_letters tl ON tl.id = tlr.time_letter_id
SET tlr.delivered_at = NULL
WHERE tl.delivery_mode = 'DATE'
  AND tl.status <> 'SENT'
  AND tlr.delivered_at IS NOT NULL;

-- 이미 발송된 DATE 연결은 타임레터의 실제 발송 완료 시각으로 맞춘다.
UPDATE time_letter_receiver tlr
JOIN time_letters tl ON tl.id = tlr.time_letter_id
SET tlr.delivered_at = tl.delivered_at
WHERE tl.delivery_mode = 'DATE'
  AND tl.status = 'SENT'
  AND tl.delivered_at IS NOT NULL
  AND (tlr.delivered_at IS NULL OR tlr.delivered_at <> tl.delivered_at);

-- 이미 충족된 사후 전달 조건은 수신자별 실제 전달 시각으로 이관한다.
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
  AND (tlr.delivered_at IS NULL OR tlr.delivered_at <> dc.fulfilled_at);

-- 실제 전달된 수신자가 있는 사후 타임레터의 상태를 동기화한다.
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
  AND tl.status <> 'DRAFT';

-- 실제 전달 시각은 수신자 연결에만 저장하므로 타임레터의 중복 컬럼을 제거한다.
ALTER TABLE time_letters DROP COLUMN delivered_at;
