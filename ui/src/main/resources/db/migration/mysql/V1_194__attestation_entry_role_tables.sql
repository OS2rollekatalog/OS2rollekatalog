CREATE TABLE attestation_organisation_role_attestation_entry_user_role (
   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   attestation_organisation_role_attestation_entry_id BIGINT NOT NULL,
   user_role VARCHAR(255) NULL,
   CONSTRAINT fk_attestation_org_role_userrole_entry FOREIGN KEY (attestation_organisation_role_attestation_entry_id) REFERENCES attestation_organisation_role_attestation_entry (attestation_id) ON DELETE CASCADE
);

CREATE TABLE attestation_organisation_role_attestation_entry_role_group (
   id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
   attestation_organisation_role_attestation_entry_id BIGINT NOT NULL,
   role_group VARCHAR(255) NULL,
    CONSTRAINT fk_attestation_org_role_rolegroup_entry FOREIGN KEY (attestation_organisation_role_attestation_entry_id) REFERENCES attestation_organisation_role_attestation_entry (attestation_id) ON DELETE CASCADE
);