-- 참고용 DDL. 실제 적용은 앱 기동 시 MysqlSchemaCompatibilityMigrator 가 수행한다.
-- (#167) Java enum 값 추가가 MySQL ENUM/CHECK 에 막히지 않도록 VARCHAR 로 정규화한다.
--
-- 운영에서 수동 실행이 필요하면 아래만 돌리면 된다. 반복 실행해도 안전하다.
-- CHECK 가 있으면: SHOW CREATE TABLE afternote; 후 ALTER TABLE afternote DROP CHECK <name>;

ALTER TABLE afternote MODIFY COLUMN category_type VARCHAR(32);
