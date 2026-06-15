-- Join table for OUs excepted from inheritance on a user role assignment
CREATE TABLE ou_roles_excepted_ous (
    id              BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
    ou_roles_id     BIGINT       NOT NULL,
    ou_uuid         VARCHAR(36)  NOT NULL,
    FOREIGN KEY (ou_roles_id) REFERENCES ou_roles (id) ON DELETE CASCADE,
    FOREIGN KEY (ou_uuid)     REFERENCES ous (uuid)    ON DELETE CASCADE
);

-- Join table for OUs excepted from inheritance on a role group assignment
CREATE TABLE ou_rolegroups_excepted_ous (
    id                  BIGINT       NOT NULL PRIMARY KEY AUTO_INCREMENT,
    ou_rolegroups_id    BIGINT       NOT NULL,
    ou_uuid             VARCHAR(36)  NOT NULL,
    FOREIGN KEY (ou_rolegroups_id) REFERENCES ou_rolegroups (id) ON DELETE CASCADE,
    FOREIGN KEY (ou_uuid)          REFERENCES ous (uuid)         ON DELETE CASCADE
);

-- Flag columns so we can quickly filter assignments that use this feature
ALTER TABLE ou_roles      ADD COLUMN contains_excepted_ous BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ou_rolegroups ADD COLUMN contains_excepted_ous BOOLEAN NOT NULL DEFAULT FALSE;
