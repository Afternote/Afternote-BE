-- Apply once only when Hibernate ddl-auto=update / the MySQL schema compatibility
-- migrator are NOT managing this existing table. Do not re-run after auto-update.
-- Existing expires_at values are intentionally preserved (including old 7-day links).
-- If status has a legacy CHECK constraint, remove that table-specific constraint
-- before applying this change; new values are PENDING/ACCEPTED/DECLINED/EXPIRED.
ALTER TABLE receiver_invitation
    MODIFY COLUMN status VARCHAR(32) NOT NULL,
    ADD COLUMN declined_user_id BIGINT NULL;
