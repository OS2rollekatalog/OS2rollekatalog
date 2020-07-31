ALTER TABLE users ADD attestation_deadline DATETIME2 NULL;
ALTER TABLE users ADD attestation_helpdesk_notified BIT NOT NULL DEFAULT 0;
ALTER TABLE users ADD attestation_manager_notified BIT NOT NULL DEFAULT 0;

ALTER TABLE positions ADD attestation_deadline DATETIME2 NULL;
ALTER TABLE positions ADD attestation_helpdesk_notified BIT NOT NULL DEFAULT 0;
ALTER TABLE positions ADD attestation_manager_notified BIT NOT NULL DEFAULT 0;
