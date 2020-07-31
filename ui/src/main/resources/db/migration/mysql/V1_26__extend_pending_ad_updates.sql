ALTER TABLE pending_ad_updates MODIFY COLUMN user_id VARCHAR(64);
ALTER TABLE pending_ad_updates ADD COLUMN it_system_id BIGINT;