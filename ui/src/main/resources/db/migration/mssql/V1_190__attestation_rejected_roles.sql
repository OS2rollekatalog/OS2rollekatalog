CREATE TABLE attestation_it_system_user_attestation_entry_role_group (
   id BIGINT IDENTITY(1,1) PRIMARY KEY,
   attestation_it_system_user_attestation_entry_id BIGINT NOT NULL,
   role_group NVARCHAR(255) NULL,
   CONSTRAINT fk_attestation_rejected_role_group_it_system_user_attestation
   FOREIGN KEY (attestation_it_system_user_attestation_entry_id)
   REFERENCES attestation_it_system_user_attestation_entry (id)
   ON DELETE CASCADE
);

CREATE TABLE attestation_it_system_user_attestation_entry_user_role (
   id BIGINT IDENTITY(1,1) PRIMARY KEY,
   attestation_it_system_user_attestation_entry_id BIGINT NOT NULL,
   user_role NVARCHAR(255) NULL,
   CONSTRAINT fk_attestation_rejected_user_role_it_system_user_attestation
   FOREIGN KEY (attestation_it_system_user_attestation_entry_id)
   REFERENCES attestation_it_system_user_attestation_entry (id)
   ON DELETE CASCADE
);

CREATE TABLE attestation_organisation_user_attestation_entry_user_role (
   id BIGINT IDENTITY(1,1) PRIMARY KEY,
   attestation_organisation_user_attestation_entry_id BIGINT NOT NULL,
   user_role NVARCHAR(255) NULL,
   CONSTRAINT fk_attestation_rejected_user_role_organisation_user_attestation
   FOREIGN KEY (attestation_organisation_user_attestation_entry_id)
   REFERENCES attestation_organisation_user_attestation_entry (id)
   ON DELETE CASCADE
);

CREATE TABLE attestation_organisation_user_attestation_entry_role_group (
   id BIGINT IDENTITY(1,1) PRIMARY KEY,
   attestation_organisation_user_attestation_entry_id BIGINT NOT NULL,
   role_group NVARCHAR(255) NULL,
   CONSTRAINT fk_attestation_rejected_role_group_organisation_user_attestation
   FOREIGN KEY (attestation_organisation_user_attestation_entry_id)
   REFERENCES attestation_organisation_user_attestation_entry (id)
   ON DELETE CASCADE
);
