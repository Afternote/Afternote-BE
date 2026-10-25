-- One-time upgrade from 2e7c36c for MySQL 8 when ddl-auto=update is NOT managing the schema.
-- The existing MysqlSchemaCompatibilityMigrator converts native enum columns / legacy CHECK constraints.
-- Do not execute after auto-update has already created these columns/tables.
ALTER TABLE diary ADD COLUMN published_at DATETIME(6) NULL;
ALTER TABLE deep_thought ADD COLUMN published_at DATETIME(6) NULL;
ALTER TABLE user_daily_question ADD COLUMN published_at DATETIME(6) NULL;
-- Historical first-finalization timestamps were not stored. created_at is the documented approximation.
UPDATE diary SET published_at = created_at WHERE is_draft = FALSE;
UPDATE deep_thought SET published_at = created_at WHERE is_draft = FALSE;
UPDATE user_daily_question SET published_at = created_at WHERE is_draft = FALSE AND is_answered = TRUE;
ALTER TABLE notification_dispatch
    MODIFY COLUMN status VARCHAR(32) NOT NULL,
    ADD COLUMN channel VARCHAR(32) NULL,
    ADD COLUMN recipient_phone VARCHAR(255) NULL,
    ADD COLUMN push_recipient_id BIGINT NULL,
    ADD COLUMN external_recipient BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN push_accepted BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE notification_dispatch SET push_accepted = TRUE WHERE status = 'PUSH_SENT';
-- delivered_at now records actual delivery, not the planned send_at.
ALTER TABLE time_letter_receiver MODIFY COLUMN delivered_at DATETIME(6) NULL;
UPDATE time_letter_receiver tr JOIN time_letters tl ON tl.id = tr.time_letter_id
    SET tr.delivered_at = NULL WHERE tl.status <> 'SENT' AND tl.delivery_mode = 'DATE';
CREATE TABLE notification_profile (
    user_id BIGINT PRIMARY KEY,
    daily_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    daily_time TIME NOT NULL DEFAULT '18:00:00',
    weekdays INT NOT NULL DEFAULT 127,
    weekly_goal INT NOT NULL DEFAULT 3,
    revision BIGINT NOT NULL DEFAULT 0,
    delegate_name VARCHAR(255) NULL,
    delegate_phone VARCHAR(255) NULL,
    delegate_user_id BIGINT NULL,
    delegate_version VARCHAR(255) NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_cycle DATETIME(6) NULL,
    return_pending BOOLEAN NOT NULL DEFAULT FALSE,
    cancellation_pending BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE notification_anniversary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    anniversary_month INT NOT NULL,
    anniversary_day INT NOT NULL,
    INDEX idx_anniversary_owner (user_id)
);
CREATE TABLE notification_link (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    cycle DATETIME(6) NOT NULL,
    delegate_version VARCHAR(255) NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL
);
CREATE TABLE notification_channel_attempt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dispatch_id BIGINT NOT NULL,
    channel VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    provider_message_id VARCHAR(255) NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    CONSTRAINT uk_notification_channel UNIQUE (dispatch_id, channel),
    INDEX idx_channel_retry (next_attempt_at, id)
);
CREATE TABLE notification_letter_delivery_run (
    run_key VARCHAR(160) PRIMARY KEY,
    letter_id BIGINT NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    delivered_count INT NOT NULL,
    failed_count INT NOT NULL,
    INDEX idx_letter_delivery_run (letter_id)
);
