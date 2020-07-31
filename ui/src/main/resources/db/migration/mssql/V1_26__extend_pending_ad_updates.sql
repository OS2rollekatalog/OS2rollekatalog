ALTER TABLE pending_ad_updates ALTER COLUMN user_id NVARCHAR(64);
ALTER TABLE pending_ad_updates ADD it_system_id BIGINT;
