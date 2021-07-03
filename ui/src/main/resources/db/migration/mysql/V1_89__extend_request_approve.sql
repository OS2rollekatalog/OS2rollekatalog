ALTER TABLE request_approve ADD COLUMN requested_for_uuid VARCHAR(36) REFERENCES users(uuid);
ALTER TABLE request_approve ADD COLUMN email_sent BOOLEAN DEFAULT 0;
ALTER TABLE request_approve ADD COLUMN ou_uuid VARCHAR(36) REFERENCES ous(uuid);
-- cannot drop easily, foreign key *sigh*
-- ALTER TABLE request_approve DROP COLUMN manager_uuid;