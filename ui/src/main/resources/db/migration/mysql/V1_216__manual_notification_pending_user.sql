CREATE TABLE manual_notification_pending_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_uuid   VARCHAR(36)  NOT NULL,
    created_at  DATETIME     NOT NULL,
    CONSTRAINT uq_manual_notification_pending_user UNIQUE (user_uuid)
);
