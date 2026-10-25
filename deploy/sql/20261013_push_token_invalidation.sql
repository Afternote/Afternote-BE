-- Run once before deploying token invalidation support on existing databases.
ALTER TABLE user_push_tokens
    ADD COLUMN invalidated_at DATETIME(6) NULL,
    ADD COLUMN invalidation_reason VARCHAR(32) NULL,
    ADD COLUMN notification_permission VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN push_enabled BIT NOT NULL DEFAULT b'1';

CREATE TABLE push_installations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    installation_id VARCHAR(36) NOT NULL,
    permission VARCHAR(16) NOT NULL,
    CONSTRAINT uk_push_installation_user UNIQUE (user_id, installation_id),
    CONSTRAINT fk_push_installation_user FOREIGN KEY (user_id) REFERENCES users(id)
);
ALTER TABLE user_push_tokens ADD COLUMN installation_id BIGINT NULL,
    ADD CONSTRAINT fk_push_token_installation FOREIGN KEY (installation_id) REFERENCES push_installations(id);
