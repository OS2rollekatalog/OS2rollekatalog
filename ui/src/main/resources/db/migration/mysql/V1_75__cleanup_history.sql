-- remove redundant history (can be computed from other tables
DELETE FROM history_role_assignments WHERE assigned_through_type IN ('ORGUNIT', 'TITLE');

-- Remove redundant history (we lookup inherited KLE from the ou tables)
-- but keep 1 week just to ensure we can generate reports containing direct assignments from the last week
DELETE FROM history_kle_assignments WHERE dato < NOW() - INTERVAL 1 WEEK;

ALTER TABLE history_ous_users ADD COLUMN id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT;
ALTER TABLE history_ous_users ADD COLUMN title_uuid VARCHAR(36);

-- Remove bad history (the reference uuid was taken from the title and not the OU on previous records)
DELETE FROM history_title_role_assignments;