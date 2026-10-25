-- Run once when Hibernate schema auto-update is not managing this new table.
CREATE TABLE notification_dispatch (
 id BIGINT AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 event_key VARCHAR(160) NOT NULL,
 category VARCHAR(16) NOT NULL,
 event VARCHAR(32) NOT NULL,
 title VARCHAR(200) NOT NULL,
 body VARCHAR(1000) NOT NULL,
 payload_json VARCHAR(4000) NOT NULL,
 due_at DATETIME(6) NOT NULL,
 status VARCHAR(32) NOT NULL,
 selected_date DATE NULL,
 read_at DATETIME(6) NULL,
 visible_at DATETIME(6) NULL,
 delivery_reason VARCHAR(255) NULL,
 CONSTRAINT uk_notification_user_event UNIQUE (user_id, event_key),
 INDEX idx_notification_due (status, due_at)
);
