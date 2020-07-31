ALTER TABLE users ADD COLUMN attestation_deadline TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN attestation_helpdesk_notified BOOLEAN NULL;
ALTER TABLE users ADD COLUMN attestation_manager_notified BOOLEAN NULL;

UPDATE users SET attestation_helpdesk_notified = 0;
UPDATE users SET attestation_manager_notified = 0;

ALTER TABLE users MODIFY COLUMN attestation_helpdesk_notified BIT NOT NULL DEFAULT 0;
ALTER TABLE users MODIFY COLUMN attestation_manager_notified BIT NOT NULL DEFAULT 0;

ALTER TABLE positions ADD COLUMN attestation_deadline TIMESTAMP NULL;
ALTER TABLE positions ADD COLUMN attestation_helpdesk_notified BOOLEAN NULL;
ALTER TABLE positions ADD COLUMN attestation_manager_notified BOOLEAN NULL;

UPDATE positions SET attestation_helpdesk_notified = 0;
UPDATE positions SET attestation_manager_notified = 0;

ALTER TABLE positions MODIFY COLUMN attestation_helpdesk_notified BIT NOT NULL DEFAULT 0;
ALTER TABLE positions MODIFY COLUMN attestation_manager_notified BIT NOT NULL DEFAULT 0;
