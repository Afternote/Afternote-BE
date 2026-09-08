-- 카카오톡 공유 기반 수신자 초대 테이블과 회원 연결 식별자를 생성한다.
-- 운영 DB에서 여러 번 실행해도 안전하도록 컬럼과 인덱스 존재 여부를 확인한다.

CREATE TABLE IF NOT EXISTS receiver_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inviter_user_id BIGINT NOT NULL,
    accepted_user_id BIGINT NULL,
    receiver_id BIGINT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_receiver_invitation_token_hash UNIQUE (token_hash),
    INDEX idx_receiver_invitation_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @receiver_invitation_token_unique_exists := (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'receiver_invitation'
        GROUP BY INDEX_NAME
        HAVING MAX(NON_UNIQUE) = 0
           AND GROUP_CONCAT(
               COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','
           ) = 'token_hash'
    ) compatible_unique_indexes
);

SET @receiver_invitation_token_unique_ddl := IF(
    @receiver_invitation_token_unique_exists = 0,
    'ALTER TABLE receiver_invitation ADD CONSTRAINT uk_receiver_invitation_token_hash UNIQUE (token_hash)',
    'SELECT 1'
);

PREPARE receiver_invitation_token_unique_statement FROM @receiver_invitation_token_unique_ddl;
EXECUTE receiver_invitation_token_unique_statement;
DEALLOCATE PREPARE receiver_invitation_token_unique_statement;

SET @receiver_invitation_expires_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'receiver_invitation'
      AND INDEX_NAME = 'idx_receiver_invitation_expires_at'
);

SET @receiver_invitation_expires_index_ddl := IF(
    @receiver_invitation_expires_index_exists = 0,
    'CREATE INDEX idx_receiver_invitation_expires_at ON receiver_invitation (expires_at)',
    'SELECT 1'
);

PREPARE receiver_invitation_expires_index_statement FROM @receiver_invitation_expires_index_ddl;
EXECUTE receiver_invitation_expires_index_statement;
DEALLOCATE PREPARE receiver_invitation_expires_index_statement;

SET @receiver_accepted_user_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'receiver'
      AND COLUMN_NAME = 'accepted_user_id'
);

SET @receiver_accepted_user_column_ddl := IF(
    @receiver_accepted_user_column_exists = 0,
    'ALTER TABLE receiver ADD COLUMN accepted_user_id BIGINT NULL AFTER user_id',
    'SELECT 1'
);

PREPARE receiver_accepted_user_column_statement FROM @receiver_accepted_user_column_ddl;
EXECUTE receiver_accepted_user_column_statement;
DEALLOCATE PREPARE receiver_accepted_user_column_statement;

SET @receiver_owner_accepted_user_unique_exists := (
    SELECT COUNT(*)
    FROM (
        SELECT INDEX_NAME
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'receiver'
        GROUP BY INDEX_NAME
        HAVING MAX(NON_UNIQUE) = 0
           AND GROUP_CONCAT(
               COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ','
           ) = 'user_id,accepted_user_id'
    ) compatible_unique_indexes
);

SET @receiver_owner_accepted_user_unique_ddl := IF(
    @receiver_owner_accepted_user_unique_exists = 0,
    'ALTER TABLE receiver ADD CONSTRAINT uk_receiver_owner_accepted_user UNIQUE (user_id, accepted_user_id)',
    'SELECT 1'
);

PREPARE receiver_owner_accepted_user_unique_statement FROM @receiver_owner_accepted_user_unique_ddl;
EXECUTE receiver_owner_accepted_user_unique_statement;
DEALLOCATE PREPARE receiver_owner_accepted_user_unique_statement;

-- 앱 수신자는 이제 로그인 계정(accepted_user_id)으로 식별하므로 기존 접근 코드를 제거한다.
SET @receiver_auth_code_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'receiver'
      AND COLUMN_NAME = 'auth_code'
);

SET @receiver_auth_code_column_ddl := IF(
    @receiver_auth_code_column_exists > 0,
    'ALTER TABLE receiver DROP COLUMN auth_code',
    'SELECT 1'
);

PREPARE receiver_auth_code_column_statement FROM @receiver_auth_code_column_ddl;
EXECUTE receiver_auth_code_column_statement;
DEALLOCATE PREPARE receiver_auth_code_column_statement;
