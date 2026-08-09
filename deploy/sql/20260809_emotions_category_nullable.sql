-- #139: PENDING 감정 행 생성을 위해 emotion_category NULL 허용
-- Hibernate ddl-auto=update 는 NOT NULL → NULL 변경을 하지 않음
ALTER TABLE emotions MODIFY COLUMN emotion_category VARCHAR(30) NULL;
