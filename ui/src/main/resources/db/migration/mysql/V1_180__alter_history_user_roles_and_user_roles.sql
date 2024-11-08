ALTER TABLE user_roles ADD extra_sensitive_role BOOLEAN NOT NULL AFTER `sensitive_role`;
ALTER TABLE history_user_roles ADD extra_sensitive_role BOOLEAN NULL AFTER `sensitive_role`;
ALTER TABLE attestation_run CHANGE super_sensitive extra_sensitive bit(1) DEFAULT NULL NULL;
