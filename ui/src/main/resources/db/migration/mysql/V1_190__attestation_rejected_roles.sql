CREATE TABLE attestation_it_system_user_attestation_entry_role_group (
   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   attestation_it_system_user_attestation_entry_id BIGINT NOT NULL,
   role_group VARCHAR(255) NULL
);

ALTER TABLE attestation_it_system_user_attestation_entry_role_group ADD CONSTRAINT fk_attestation_rejected_role_group_it_system_user_attestation FOREIGN KEY (attestation_it_system_user_attestation_entry_id) REFERENCES attestation_it_system_user_attestation_entry (id) ON DELETE CASCADE;

CREATE TABLE attestation_it_system_user_attestation_entry_user_role (
   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   attestation_it_system_user_attestation_entry_id BIGINT NOT NULL,
   user_role VARCHAR(255) NULL
);

ALTER TABLE attestation_it_system_user_attestation_entry_user_role ADD CONSTRAINT fk_attestation_rejected_user_role_it_system_user_attestation FOREIGN KEY (attestation_it_system_user_attestation_entry_id) REFERENCES attestation_it_system_user_attestation_entry (id) ON DELETE CASCADE;

CREATE TABLE attestation_organisation_user_attestation_entry_user_role (
   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   attestation_organisation_user_attestation_entry_id BIGINT NOT NULL,
   user_role VARCHAR(255) NULL
);

ALTER TABLE attestation_organisation_user_attestation_entry_user_role ADD CONSTRAINT fk_attestation_rejected_user_role_organisation_user_attestation FOREIGN KEY (attestation_organisation_user_attestation_entry_id) REFERENCES attestation_organisation_user_attestation_entry (id) ON DELETE CASCADE;

CREATE TABLE attestation_organisation_user_attestation_entry_role_group (
   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   attestation_organisation_user_attestation_entry_id BIGINT NOT NULL,
   role_group VARCHAR(255) NULL
);

ALTER TABLE attestation_organisation_user_attestation_entry_role_group ADD CONSTRAINT fk_attestation_rejected_role_group_organisation_user_attestation FOREIGN KEY (attestation_organisation_user_attestation_entry_id) REFERENCES attestation_organisation_user_attestation_entry (id) ON DELETE CASCADE;