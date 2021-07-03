ALTER TABLE request_approve ADD requested_for_uuid NVARCHAR(36) FOREIGN KEY REFERENCES users(uuid);
ALTER TABLE request_approve ADD email_sent BIT DEFAULT 0;
ALTER TABLE request_approve ADD ou_uuid NVARCHAR(36) FOREIGN KEY REFERENCES ous(uuid);
-- cannot drop easily, foreign key *sigh*
-- ALTER TABLE request_approve DROP manager_uuid;
