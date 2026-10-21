ALTER TABLE manual_assignment_notification_map
    ADD COLUMN IF NOT EXISTS removed_by VARCHAR(255) NULL, ALGORITHM=INSTANT;

CREATE INDEX IF NOT EXISTS idx_manual_assignment_notification_map_user ON manual_assignment_notification_map (domain_id, user_user_id);
